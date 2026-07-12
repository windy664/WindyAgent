package org.windy.windyagent.platform.bukkit.fs

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.util.logging.Logger

/**
 * 用 JGit 给「Agent 改过的文件」做版本化——**这是自动改配置/装插件的撤销键 + 异地备份**。
 *
 * <p>关键取舍：<b>只跟踪 Agent 真正动过的文件</b>（每次写/删只 `git add`/`git rm` 那一个路径），
 * 绝不 `add .`。否则 git 会把整个服务器目录（含存档 world/、日志、jar）吞进去，仓库瞬间爆炸。
 * 这样仓库里永远只有配置类小文件，`revert` 也正好只回滚 Agent 的改动。
 *
 * <p><b>本地优先</b>：只 `init` 一个本地仓库即拥有历史 / diff / 回滚，无需远端。远端（[remoteUrl]）是
 * <b>可选</b>的异地备份：provider 无关，填任意 Git over HTTPS 地址（Gitee/GitHub/GitLab/自建）+ token 即可，
 * 默认不开。⚠️ 一旦开 push，被提交的配置里若含密钥会被推到远端——务必用私有仓库并自行排除敏感文件。
 */
class GitRepoService(
    private val root: File,
    private val log: Logger,
    private val remoteUrl: String = "",
    private val remoteBranch: String = "master",
    private val remoteUser: String = "",
    private val remoteToken: String = "",
    private val autoPush: Boolean = false,
    /** 单文件提交上限：超过则跳过不提交（防大文件进仓库撑爆）。默认 1MB。 */
    private val maxCommitBytes: Long = 1_048_576L
) {
    private val author = PersonIdent("WindyAgent", "agent@windyagent.local")

    private companion object {
        // 二进制/易膨胀扩展名：一律不进 git（jar 走"提交清单"而非本体）。
        val BLOCKED_EXT = setOf("jar", "zip", "gz", "tar", "rar", "7z", "db", "sqlite", "mca", "dat",
            "png", "jpg", "jpeg", "gif", "ico", "class", "log", "bin", "exe", "so", "dll")
        // init 时写入的默认 .gitignore：兜底排除大数据/日志/二进制，防漏网。
        // 注意：这里<b>不</b>列存档目录——①存档名可变（level-name 可改），硬编 "world" 既不准又会误伤
        //   同名存档下的 serverconfig；②存档本就被「作用域 files.roots 只允许 plugins/config/serverconfig」
        //   挡在外，且本仓库从不 `git add .`、只提交 Agent 动过的路径，故存档根本不会进库，无需在此再排。
        val DEFAULT_GITIGNORE = """
            # WindyAgent 配置版本化：只跟踪 Agent 改过的配置类小文件，排除大数据/日志/二进制。
            logs/
            crash-reports/
            cache/
            *.jar
            *.zip
            *.gz
            *.db
            *.sqlite
            *.mca
            *.log
        """.trimIndent()
    }

    /** 确保 root 是个 git 仓库（首次自动 init + 写默认 .gitignore）。失败抛出，由调用方兜。 */
    private fun open(): Git =
        if (File(root, ".git").exists()) Git.open(root)
        else Git.init().setDirectory(root).call().also {
            log.info("[Git] 已在服务器目录初始化本地仓库用于配置版本化：${root.absolutePath}")
            runCatching {
                val gi = File(root, ".gitignore")
                if (!gi.exists()) gi.writeText(DEFAULT_GITIGNORE + "\n")
            }
        }

    /** 该相对路径是否允许进 git：已删的允许（走 rm）；存在的须非二进制扩展 + 不超上限。 */
    private fun trackable(rel: String): Boolean {
        val f = File(root, rel)
        if (!f.exists()) return true // 删除操作
        val ext = f.extension.lowercase()
        if (ext in BLOCKED_EXT) return false
        if (f.length() > maxCommitBytes) return false
        return true
    }

    /**
     * 在 Agent 改动/删除某文件<b>之前</b>，先把它当前磁盘内容提交成一版<b>基线</b>。
     *
     * <p>为什么必须有这一步：git 只在文件被提交时才"见到"它的内容。若第一次接触某文件就直接
     * 提交 Agent 改后的版本，git 记下的初始版本就是"改后"，原始内容从未入库——此时
     * {@code revert} 这条"新增"提交只会把文件<b>删掉</b>，而非还原成原始。先存基线后，历史成为
     * 「原始 → 改动」，回滚 Agent 的改动才能真正回到原始内容。
     *
     * <p>文件不存在（Agent 新建）→ 无基线可存，跳过（此时回滚=删除该新建文件，本就正确）。
     * 已跟踪且内容一致 → status 无变化，不产生多余提交。二进制/超大 → 本就不进 git，跳过。
     * 失败只记日志、不抛（基线是尽力而为，不该挡住主改动）。
     */
    fun baselineBeforeChange(rel: String) {
        val p = rel.replace('\\', '/')
        val f = File(root, p)
        if (!f.exists() || !trackable(p)) return
        runCatching {
            open().use { git ->
                git.add().addFilepattern(p).call()
                val st = git.status().call()
                // 仅当"确有未入库内容"（此前未跟踪 or 外部/插件改过）才提交基线；已是最新则跳过
                if (st.added.contains(p) || st.changed.contains(p)) {
                    git.commit().setMessage("基线：$p 改动前的原始内容").setAuthor(author).setCommitter(author).call()
                    log.info("[Git] 已为 $p 存改动前基线")
                }
            }
        }.onFailure { log.warning("[Git] 存基线失败（$p）：${it.message}") }
    }

    /**
     * 暂存指定相对路径（已删的走 rm，存在的走 add）并提交。
     * 超大/二进制文件跳过不提交（在返回信息里点名），避免大数据进仓库。
     * @return 短 commit id + 摘要；无改动返回提示。
     */
    fun commit(relPaths: List<String>, message: String): String {
        if (relPaths.isEmpty()) return "无文件需提交"
        val skipped = ArrayList<String>()
        open().use { git ->
            var staged = 0
            for (rel in relPaths) {
                val p = rel.replace('\\', '/')
                if (!trackable(p)) { skipped += p; continue }
                if (File(root, p).exists()) git.add().addFilepattern(p).call()
                else git.rm().addFilepattern(p).call()
                staged++
            }
            val skipNote = if (skipped.isEmpty()) "" else "（未纳入版本化：${skipped.joinToString("、")}——大文件/二进制不提交）"
            if (staged == 0) return "无文件纳入版本化$skipNote"
            if (git.status().call().let { it.added.isEmpty() && it.changed.isEmpty() && it.removed.isEmpty() })
                return "无实际改动（内容未变），未生成提交$skipNote"
            val commit = git.commit().setMessage(message).setAuthor(author).setCommitter(author).call()
            val short = commit.name.take(8)
            var extra = ""
            if (autoPush && remoteUrl.isNotBlank()) extra = "；" + push(git)
            return "已提交 $short：$message$extra$skipNote"
        }
    }

    /** 最近提交历史（可限定某路径）。 */
    fun history(rel: String?, limit: Int): String {
        open().use { git ->
            if (git.repository.resolve("HEAD") == null) return "（暂无提交历史）"
            val cmd = git.log().setMaxCount(limit.coerceIn(1, 50))
            if (!rel.isNullOrBlank()) cmd.addPath(rel.replace('\\', '/'))
            val out = StringBuilder()
            for (c in cmd.call()) {
                val t = java.time.Instant.ofEpochSecond(c.commitTime.toLong())
                out.append("• ").append(c.name.take(8)).append("  ")
                    .append(t).append("  ").append(c.shortMessage).append('\n')
            }
            return if (out.isEmpty()) "（暂无提交历史）" else out.toString().trimEnd()
        }
    }

    /** 回滚：对指定 commit 生成一个反向提交（git revert），不改写历史。 */
    fun revert(commitId: String): String {
        open().use { git ->
            val id: ObjectId = git.repository.resolve(commitId)
                ?: return "找不到提交：$commitId"
            val res = git.revert().include(id).call()
            var extra = ""
            if (autoPush && remoteUrl.isNotBlank()) extra = "；" + push(git)
            return if (res != null) "已回滚提交 ${commitId.take(8)}（生成反向提交 ${res.name.take(8)}）$extra"
            else "回滚未完成（可能存在冲突，请人工处理）：$commitId"
        }
    }

    /** 手动推送到远端。远端未配置则提示。 */
    fun pushNow(): String {
        if (remoteUrl.isBlank()) return "未配置远端仓库（files.git.remote 为空），仅本地版本化"
        open().use { git -> return push(git) }
    }

    private fun push(git: Git): String = runCatching {
        val cmd = git.push().setRemote(remoteUrl)
        if (remoteToken.isNotBlank())
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(remoteUser.ifBlank { "token" }, remoteToken))
        cmd.call()
        "已推送到远端"
    }.getOrElse { "推送远端失败：${it.message}" }
}
