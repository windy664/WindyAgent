package org.windy.windyagent

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent
import org.windy.windyagent.agent.ReActAgent
import org.windy.windyagent.platform.SessionManager
import org.windy.windyagent.platform.neoforge.AgentChatListener
import org.windy.windyagent.platform.neoforge.NeoForgePlatform

@Mod(Windyagent.MODID)
class Windyagent(modEventBus: IEventBus, modContainer: ModContainer) {

    companion object {
        const val MODID = "windyagent"
        private val LOGGER = LogUtils.getLogger()
    }

    init {
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        LOGGER.info("WindyAgent initializing...")

        val cfg = runCatching {
            AgentConfig.load(FMLPaths.CONFIGDIR.get().resolve("windyagent"))
        }.getOrElse {
            LOGGER.error("Failed to load windyagent-config.yml: {}", it.message)
            return
        }

        val llm = runCatching { buildProvider(cfg) }.getOrElse {
            LOGGER.error(it.message)
            return
        }

        val platform = NeoForgePlatform(event.server)
        val agent = ReActAgent(llm)
        val sessions = SessionManager(cfg.maxHistory())

        NeoForge.EVENT_BUS.register(AgentChatListener(agent, platform, sessions, cfg.trigger()))
        LOGGER.info("WindyAgent started — provider: {} — trigger: '{} <message>'", llm.name, cfg.trigger())
    }
}
