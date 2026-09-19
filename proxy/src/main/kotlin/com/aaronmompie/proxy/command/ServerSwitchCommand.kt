package com.aaronmompie.proxy.command

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component

/**
 * `/server <name>` - Velocity has no built-in player-facing server switch command, and this
 * is genuinely useful for manual testing (a "no" voter or an operator peeking into Limbo/
 * Game without waiting on the automatic [com.aaronmompie.proxy.routing.RerollRoutingListener]
 * migration). Not permission-gated: this is a manual test convenience on a local network,
 * not a production feature.
 */
class ServerSwitchCommand(private val proxyServer: ProxyServer) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val player = invocation.source() as? Player ?: run {
            invocation.source().sendPlainMessage("Only players can switch servers.")
            return
        }
        val args = invocation.arguments()
        if (args.isEmpty()) {
            val names = proxyServer.allServers.joinToString(", ") { it.serverInfo.name }
            player.sendMessage(Component.text("Usage: /server <name>. Available: $names"))
            return
        }

        val target = proxyServer.getServer(args[0]).orElse(null)
        if (target == null) {
            player.sendMessage(Component.text("No such server: ${args[0]}"))
            return
        }
        player.createConnectionRequest(target).fireAndForget()
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> =
        proxyServer.allServers.map { it.serverInfo.name }
}
