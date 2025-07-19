package io.github.tw0null.multiblock.plugin

import io.github.tw0null.multiblock.PluginProvider
import org.bukkit.plugin.java.JavaPlugin

class RockballPlugin : JavaPlugin() {

    override fun onEnable() {
        PluginProvider.plugin = this
        registerEvents(this)
        logger.info("MultiBlock is ENABLED")
    }

    override fun onDisable() {
        logger.info("MultiBlock is DISABLED")
    }
}
