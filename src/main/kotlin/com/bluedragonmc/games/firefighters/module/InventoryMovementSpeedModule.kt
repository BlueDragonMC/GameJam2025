package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent

class InventoryMovementSpeedModule(val baseSpeed: Double, val slowdownPerItem: Double, val minimumSpeed: Double) : GameModule() {
    private lateinit var parent: Game
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        val instance = parent.getInstance()
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            if (instance.worldAge % 60 != 0L) return@addListener
            if (parent.state != GameState.INGAME) return@addListener
            val numItems = event.player.inventory.itemStacks.sumOf { if (it.isAir) 0 else it.amount() }
            val newSpeed = (baseSpeed - (numItems * slowdownPerItem)).coerceAtLeast(minimumSpeed)
            event.player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = newSpeed
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            event.player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1
        }
    }

    override fun deinitialize() {
        parent.players.forEach { player ->
            player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1
        }
    }
}