package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.tag.Tag

class CustomItemsModule : GameModule() {
    private val items = mutableListOf<CustomItem>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.SPECTATOR) return@addListener
            for (item in items) {
                if (item.matches(event.itemStack)) item.onUseAir(event)
            }
        }
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.SPECTATOR) return@addListener
            for (item in items) {
                if (item.matches(event.player.getItemInHand(event.hand))) item.onUseBlock(event)
            }
        }
    }

    fun addCustomItem(item: CustomItem) {
        items += item
    }
}

interface CustomItem {
    val itemId: String
    fun onUseAir(event: PlayerUseItemEvent) {}
    fun onUseBlock(event: PlayerBlockInteractEvent) {}

    fun addIdentifier(itemStack: ItemStack): ItemStack {
        return itemStack.withTag(CUSTOM_ITEM_TAG, itemId)
    }

    fun matches(itemStack: ItemStack): Boolean {
        return itemStack.getTag(CUSTOM_ITEM_TAG) == itemId
    }

    companion object {
        private val CUSTOM_ITEM_TAG = Tag.String("bluedragonmc:item")
    }
}