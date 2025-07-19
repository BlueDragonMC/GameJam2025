package com.bluedragonmc.example.server.command

import com.bluedragonmc.example.server.GAME_NAME
import com.bluedragonmc.example.server.impl.SingleGameQueue
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.command.BlueDragonCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

val JoinCommand = BlueDragonCommand("join") {
    syntax {
        if (Game.Companion.findGame(player)?.name == GAME_NAME) {
            player.sendMessage(Component.text("You are already in a game!", NamedTextColor.RED))
            return@syntax
        }
        SingleGameQueue.queue(player)
    }
}