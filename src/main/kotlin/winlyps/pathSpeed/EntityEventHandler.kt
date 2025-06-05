package winlyps.pathSpeed

import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.plugin.java.JavaPlugin

class EntityEventHandler(
    private val plugin: JavaPlugin,
    private val pathManager: PathManager,
    private val config: PathSpeedConfig
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        val entity = event.entity
        if (!config.supportedEntityTypes.contains(entity.type) || entity !is LivingEntity) return
        pathManager.cleanupEntity(entity)
        if (entity.isValid) pathManager.checkEntityPathStatus(entity)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (entity.isValid) pathManager.checkEntityPathStatus(entity)
        }, config.joinCheckDelay)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onVehicleMove(event: VehicleMoveEvent) {
        val vehicle = event.vehicle
        val to = event.to ?: return
        if (!config.supportedEntityTypes.contains(vehicle.type) || vehicle !is LivingEntity) return
        if (!pathManager.updateEntityPosition(vehicle, to)) return
        pathManager.updateEntityPathStatus(vehicle, to)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onVehicleExit(event: VehicleExitEvent) {
        val vehicle = event.vehicle
        if (!config.supportedEntityTypes.contains(vehicle.type) || vehicle !is LivingEntity) return
        pathManager.handleEntityDismount(vehicle)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityTeleport(event: EntityTeleportEvent) {
        val entity = event.entity
        if (!config.supportedEntityTypes.contains(entity.type) || entity !is LivingEntity) return
        if (pathManager.isEntityOnPath(entity)) {
            event.isCancelled = true
            plugin.logger.fine("Prevented teleportation for tracked entity: ${entity.type} at ${entity.location}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (!config.supportedEntityTypes.contains(entity.type)) return
        pathManager.cleanupEntity(entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        event.chunk.entities
            .filterIsInstance<LivingEntity>()
            .filter { config.supportedEntityTypes.contains(it.type) }
            .forEach { pathManager.cleanupEntity(it) }
    }
}
