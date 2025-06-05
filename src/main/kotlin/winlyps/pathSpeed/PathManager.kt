package winlyps.pathSpeed

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PathManager(
    private val plugin: JavaPlugin,
    private val config: PathSpeedConfig
) {
    private val entitiesOnPath = ConcurrentHashMap.newKeySet<UUID>()
    private val lastKnownPositions = ConcurrentHashMap<UUID, Triple<Int, Int, Int>>()
    private val lastOnPathTime = ConcurrentHashMap<UUID, Long>()
    private val recentlyDismountedEntities = ConcurrentHashMap<UUID, Long>()
    private val dismountGracePeriod = 5000L

    fun checkPlayerPathStatus(player: Player) = updateEntityPathStatus(player, player.location)
    fun updatePlayerPosition(player: Player, location: Location): Boolean = updateEntityPosition(player, location)
    fun cleanupPlayer(player: Player) = cleanupEntity(player)

    fun handleEntityOnPath(entity: LivingEntity) {
        val uuid = entity.uniqueId
        if (recentlyDismountedEntities.containsKey(uuid)) return

        if (entity is Player && !config.enablePlayerSpeed) {
            removeFromPath(entity)
            return
        }
        if (entity !is Player && !config.enableEntitySpeed) {
            removeFromPath(entity)
            return
        }

        lastOnPathTime[uuid] = System.currentTimeMillis()
        entitiesOnPath.add(uuid)
        applySpeedEffect(entity)
    }

    fun handleEntityOffPath(entity: LivingEntity) {
        val uuid = entity.uniqueId
        if (recentlyDismountedEntities.containsKey(uuid)) return
        val lastOnPath = lastOnPathTime[uuid] ?: 0L
        if (System.currentTimeMillis() - lastOnPath > config.gracePeriodMs) {
            removeFromPath(entity)
        }
    }

    fun checkEntityPathStatus(entity: LivingEntity) = updateEntityPathStatus(entity, entity.location)

    fun updateEntityPosition(entity: Entity, location: Location): Boolean {
        val uuid = entity.uniqueId
        if (recentlyDismountedEntities.containsKey(uuid)) return false
        if (isEntityInWater(entity, location)) return false
        val currentPos = Triple(location.blockX, location.blockY, location.blockZ)
        val lastPos = lastKnownPositions[uuid]
        if (lastPos == currentPos) return false
        lastKnownPositions[uuid] = currentPos
        return true
    }

    fun updateEntityPathStatus(entity: LivingEntity, location: Location) {
        if (isLocationNearPath(location)) handleEntityOnPath(entity)
        else handleEntityOffPath(entity)
    }

    fun cleanupEntity(entity: LivingEntity) {
        val uuid = entity.uniqueId
        entity.removePotionEffect(PotionEffectType.SPEED)
        entitiesOnPath.remove(uuid)
        lastKnownPositions.remove(uuid)
        lastOnPathTime.remove(uuid)
        recentlyDismountedEntities.remove(uuid)
    }

    fun handleEntityDismount(entity: LivingEntity) {
        val uuid = entity.uniqueId
        recentlyDismountedEntities[uuid] = System.currentTimeMillis()
        removeFromPath(entity)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            recentlyDismountedEntities.remove(uuid)
        }, dismountGracePeriod / 50L)
    }

    fun isEntityOnPath(entity: LivingEntity): Boolean {
        val uuid = entity.uniqueId
        return entitiesOnPath.contains(uuid)
    }

    fun isLocationNearPath(location: Location): Boolean {
        val world = location.world ?: return false
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        for (xOffset in -config.pathDetectionRadius..config.pathDetectionRadius) {
            for (zOffset in -config.pathDetectionRadius..config.pathDetectionRadius) {
                for (yOffset in 1..config.pathDetectionDepth) {
                    val block = world.getBlockAt(x + xOffset, y - yOffset, z + zOffset)
                    if (config.pathBlocks.contains(block.type)) return true
                }
            }
        }
        return false
    }

    private fun applySpeedEffect(entity: LivingEntity) {
        if (entity is Player && !config.enablePlayerSpeed) {
            entity.removePotionEffect(PotionEffectType.SPEED)
            return
        }
        if (entity !is Player && !config.enableEntitySpeed) {
            entity.removePotionEffect(PotionEffectType.SPEED)
            return
        }
        val currentEffect = entity.getPotionEffect(PotionEffectType.SPEED)
        if (currentEffect == null || currentEffect.duration < config.effectRefreshThreshold) {
            if (currentEffect != null) entity.removePotionEffect(PotionEffectType.SPEED)
            val effect = if (entity is Player) config.playerSpeedEffect else config.entitySpeedEffect
            entity.addPotionEffect(effect, true)
        }
    }

    private fun removeFromPath(entity: LivingEntity) {
        val uuid = entity.uniqueId
        if (entitiesOnPath.remove(uuid)) {
            entity.removePotionEffect(PotionEffectType.SPEED)
            lastOnPathTime.remove(uuid)
        }
    }

    private fun isEntityInWater(entity: Entity, location: Location): Boolean {
        val block = location.block
        val blockAbove = location.clone().add(0.0, 1.0, 0.0).block
        return block.type == Material.WATER || blockAbove.type == Material.WATER || (entity is LivingEntity && entity.isInWater)
    }

    fun startTasks() {
        startCleanupTask()
        startEffectRefreshTask()
    }

    fun cleanup() {
        entitiesOnPath.forEach { uuid ->
            plugin.server.getEntity(uuid)?.let { entity ->
                if (entity is LivingEntity) {
                    entity.removePotionEffect(PotionEffectType.SPEED)
                }
            }
        }
        entitiesOnPath.clear()
        lastKnownPositions.clear()
        lastOnPathTime.clear()
        recentlyDismountedEntities.clear()
    }

    private fun startCleanupTask() {
        object : BukkitRunnable() {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                cleanupInvalidEntities(currentTime)
                cleanupDismountRecords(currentTime)
            }
        }.runTaskTimer(plugin, config.cleanupTaskInterval, config.cleanupTaskInterval)
    }

    private fun startEffectRefreshTask() {
        object : BukkitRunnable() {
            override fun run() {
                refreshAllEffects()
            }
        }.runTaskTimer(plugin, config.effectRefreshInterval, config.effectRefreshInterval)
    }

    private fun cleanupInvalidEntities(currentTime: Long) {
        val iterator = entitiesOnPath.iterator()
        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val entity = plugin.server.getEntity(uuid) as? LivingEntity ?: run {
                iterator.remove()
                lastKnownPositions.remove(uuid)
                lastOnPathTime.remove(uuid)
                continue
            }
            if (!entity.isValid) {
                iterator.remove()
                lastKnownPositions.remove(uuid)
                lastOnPathTime.remove(uuid)
                continue
            }
            if (recentlyDismountedEntities.containsKey(uuid)) continue
            val lastOnPath = lastOnPathTime[uuid] ?: 0L
            val withinGracePeriod = currentTime - lastOnPath <= config.gracePeriodMs
            if (!isLocationNearPath(entity.location) && !withinGracePeriod) {
                iterator.remove()
                entity.removePotionEffect(PotionEffectType.SPEED)
                lastOnPathTime.remove(uuid)
            }
        }
    }

    private fun cleanupDismountRecords(currentTime: Long) {
        recentlyDismountedEntities.entries.removeIf { (_, dismountTime) ->
            currentTime - dismountTime > dismountGracePeriod
        }
    }

    private fun refreshAllEffects() {
        entitiesOnPath.forEach { uuid ->
            val entity = plugin.server.getEntity(uuid) as? LivingEntity
            if (entity != null && entity.isValid && !recentlyDismountedEntities.containsKey(uuid)) {
                if (entity is Player && !config.enablePlayerSpeed) {
                    removeFromPath(entity)
                    return@forEach
                }
                if (entity !is Player && !config.enableEntitySpeed) {
                    removeFromPath(entity)
                    return@forEach
                }
                applySpeedEffect(entity)
            }
        }
    }
}
