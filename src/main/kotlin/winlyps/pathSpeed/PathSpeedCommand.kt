package winlyps.pathSpeed

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin

class PathSpeedCommand(
    private val plugin: JavaPlugin,
    private val config: PathSpeedConfig,
    private val pathManager: PathManager
) : TabCompleter, org.bukkit.command.CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, alias: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§e/pathspeed reload | add <block> | remove <block> | list")
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> {
                plugin.reloadConfig()
                config.reload()
                pathManager.cleanup() // <-- Ensures all tracked entities are cleared!
                config.saveToConfig()
                plugin.saveConfig()
                sender.sendMessage("§aPathSpeed config reloaded!")
            }
            "add" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /pathspeed add <block>")
                    return true
                }
                val mat = runCatching { Material.valueOf(args[1].uppercase()) }.getOrNull()
                if (mat == null || !mat.isBlock) {
                    sender.sendMessage("§cThat is not a valid block!")
                    return true
                }
                if (config.pathBlocks.add(mat)) {
                    config.saveToConfig()
                    plugin.saveConfig()
                    sender.sendMessage("§aAdded $mat to path blocks!")
                } else {
                    sender.sendMessage("§e$mat was already a path block.")
                }
            }
            "remove" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /pathspeed remove <block>")
                    return true
                }
                val mat = runCatching { Material.valueOf(args[1].uppercase()) }.getOrNull()
                if (mat == null) {
                    sender.sendMessage("§cThat is not a valid block!")
                    return true
                }
                if (config.pathBlocks.remove(mat)) {
                    config.saveToConfig()
                    plugin.saveConfig()
                    sender.sendMessage("§aRemoved $mat from path blocks!")
                } else {
                    sender.sendMessage("§c$mat was not in path blocks.")
                }
            }
            "list" -> {
                if (config.pathBlocks.isEmpty()) {
                    sender.sendMessage("§eNo path blocks are currently set.")
                } else {
                    sender.sendMessage("§aCurrent path blocks: §f" + config.pathBlocks.joinToString(", ") { it.name })
                }
            }
            else -> sender.sendMessage("§e/pathspeed reload | add <block> | remove <block> | list")
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1)
            return listOf("reload", "add", "remove", "list").filter { it.startsWith(args[0], true) }.toMutableList()
        if (args[0].equals("add", true) && args.size == 2) {
            return Material.values()
                .filter { it.isBlock && it.name.startsWith(args[1].uppercase()) }
                .map { it.name }
                .toMutableList()
        }
        if (args[0].equals("remove", true) && args.size == 2) {
            return config.pathBlocks
                .map { it.name }
                .filter { it.startsWith(args[1].uppercase()) }
                .toMutableList()
        }
        return mutableListOf()
    }
}
