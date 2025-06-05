package winlyps.pathSpeed

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class PlayerEventHandler(
    private val plugin: JavaPlugin,
    private val pathManager: PathManager,
    private val config: PathSpeedConfig
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        pathManager.cleanupPlayer(player)
        // IMMEDIATE status check, not scheduled
        if (player.isOnline) pathManager.checkPlayerPathStatus(player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val to = event.to ?: return
        if (!pathManager.updatePlayerPosition(player, to)) return
        pathManager.updateEntityPathStatus(player, to)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pathManager.cleanupPlayer(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        pathManager.cleanupPlayer(player)
        // IMMEDIATE status check, not scheduled
        if (player.isOnline) pathManager.checkPlayerPathStatus(player)
    }
}
