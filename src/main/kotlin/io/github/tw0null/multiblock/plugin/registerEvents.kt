package io.github.tw0null.multiblock.plugin


fun registerEvents(plugin: RockballPlugin) {
    plugin.server.pluginManager.apply {
        registerEvents(io.github.tw0null.multiblock.RockSnowBall(plugin), plugin)
    }
}