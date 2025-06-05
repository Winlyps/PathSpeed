package winlyps.pathSpeed

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class PathSpeedConfig(private val plugin: JavaPlugin) {

    // Always use the plugin's config instance
    private val config: FileConfiguration
        get() = plugin.config

    var pathBlocks: MutableSet<Material> = mutableSetOf()
        private set
    val supportedEntityTypes = setOf(
        EntityType.HORSE,
        EntityType.MULE,
        EntityType.DONKEY,
        EntityType.CAMEL,
        EntityType.LLAMA,
        EntityType.TRADER_LLAMA
    )
    var enablePlayerSpeed = true
        private set
    var enableEntitySpeed = true
        private set
    var playerSpeedLevel = 1
        private set
    var entitySpeedLevel = 1
        private set
    val gracePeriodMs: Long get() = 200L
    val cleanupTaskInterval: Long get() = 4L
    val effectRefreshInterval: Long get() = 4L
    val effectRefreshThreshold: Int get() = 10
    val joinCheckDelay: Long get() = 1L
    val worldChangeCheckDelay: Long get() = 1L
    val pathDetectionRadius: Int get() = 1
    val pathDetectionDepth: Int get() = 5
    val playerSpeedEffect: PotionEffect
        get() = PotionEffect(
            PotionEffectType.SPEED, 40, playerSpeedLevel - 1, false, false, false
        )
    val entitySpeedEffect: PotionEffect
        get() = PotionEffect(
            PotionEffectType.SPEED, 40, entitySpeedLevel - 1, false, false, false
        )

    fun reload() {
        enablePlayerSpeed = config.getBoolean("enable-player-speed", true)
        enableEntitySpeed = config.getBoolean("enable-entity-speed", true)
        playerSpeedLevel = config.getInt("player-speed-level", 1).coerceIn(1, 10)
        entitySpeedLevel = config.getInt("entity-speed-level", 1).coerceIn(1, 10)
        pathBlocks = config.getStringList("path-blocks")
            .mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }
            .toMutableSet()
    }

    fun saveToConfig() {
        config.set("enable-player-speed", enablePlayerSpeed)
        config.set("enable-entity-speed", enableEntitySpeed)
        config.set("player-speed-level", playerSpeedLevel)
        config.set("entity-speed-level", entitySpeedLevel)
        config.set("path-blocks", pathBlocks.map { it.name })
    }
}
