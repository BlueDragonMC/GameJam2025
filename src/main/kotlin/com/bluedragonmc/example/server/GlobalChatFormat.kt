package com.bluedragonmc.example.server

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.utils.buildComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent

object GlobalChatFormat {
    fun hook(eventNode: EventNode<Event>) {
        val child = EventNode.event("global-chat-format", EventFilter.ALL) { true }
        child.priority = Integer.MAX_VALUE // High priority; runs last
        eventNode.addChild(child)
        child.addListener(PlayerChatEvent::class.java) { event ->
            val player = event.player as CustomPlayer

            val team = Game.findGame(event.player)?.getModuleOrNull<TeamModule>()?.getTeam(event.player)
            val nameColor = team?.name?.color()
            val prefix = team?.scoreboardTeam?.prefix

            val component = buildComponent {
                +(prefix ?: Component.empty()).append(player.name.decoration(TextDecoration.BOLD, false).let { if (nameColor != null) it.color(nameColor) else it })
                +Component.text(": ", NamedTextColor.DARK_GRAY)
                +Component.text(event.rawMessage, NamedTextColor.WHITE)
            }
            event.formattedMessage = component
        }
    }
}