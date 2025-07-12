package com.bluedragonmc.example.server

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.utils.buildComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
            val component = buildComponent {
                +player.name
                +Component.text(": ", NamedTextColor.DARK_GRAY)
                +Component.text(event.rawMessage, NamedTextColor.WHITE)
            }
            event.formattedMessage = component
        }
    }
}