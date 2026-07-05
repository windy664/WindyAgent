package org.windy.windyagent.bridge

/**
 * IM 平台联动<b>注册表</b> —— 唯一的扩展点，Velocity 与 Bukkit 两端共用。
 *
 * 两端主类各自把平台差异（怎么定位插件、Agent 调用链）包成一个 [InstallEnv]，然后调 [installAll]。
 * 新增一个 IM 平台（飞书/钉钉/企业微信…）只需：写一个 [ImConnector] 实现，在下方 [connectors]
 * 加一行 —— 两端主类与 Agent 核心均无需改动。
 *
 * <p>每个 connector 自负「检测宿主是否存在」，未安装则静默跳过；故这里对全部平台一律尝试装载。
 */
object ImConnectors {

    /** 已知的 IM 平台联动器。加平台 = 在此加一行。 */
    private val connectors: List<ImConnector> = listOf(
        XingtuBotConnector,
        // 未来：FeishuConnector, DingtalkConnector, WeComConnector …
    )

    /** 对所有已知平台尝试装载联动（两端主类共用）。 */
    fun installAll(env: InstallEnv) {
        for (c in connectors) {
            runCatching {
                if (c.tryInstall(env)) env.logger.info("[IM] 联动已启用：${c.platformId}")
            }.onFailure { env.logger.warn("[IM] ${c.platformId} 联动装载失败：${it.message}") }
        }
    }
}
