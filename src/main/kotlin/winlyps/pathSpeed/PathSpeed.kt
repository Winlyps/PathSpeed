package winlyps.pathSpeed

import org.bukkit.plugin.java.JavaPlugin

class PathSpeed : JavaPlugin() {
    lateinit var configInstance: PathSpeedConfig
        private set
    private lateinit var pathManager: PathManager
    private lateinit var playerEventHandler: PlayerEventHandler
    private lateinit var entityEventHandler: EntityEventHandler

    override fun onEnable() {
        saveDefaultConfig()
        configInstance = PathSpeedConfig(this)
        configInstance.reload()

        pathManager = PathManager(this, configInstance)
        playerEventHandler = PlayerEventHandler(this, pathManager, configInstance)
        entityEventHandler = EntityEventHandler(this, pathManager, configInstance)
        server.pluginManager.registerEvents(playerEventHandler, this)
        server.pluginManager.registerEvents(entityEventHandler, this)
        pathManager.startTasks()

        val cmd = getCommand("pathspeed")
        val completer = PathSpeedCommand(this, configInstance, pathManager) // pass pathManager here
        cmd?.setExecutor(completer)
        cmd?.tabCompleter = completer

        logger.info("PathSpeed plugin enabled successfully!")
    }

    override fun onDisable() {
        pathManager.cleanup()
        logger.info("PathSpeed plugin disabled successfully!")
    }
}
