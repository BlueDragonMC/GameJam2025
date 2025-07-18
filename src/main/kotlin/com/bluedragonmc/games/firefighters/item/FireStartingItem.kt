package com.bluedragonmc.games.firefighters.item

import com.bluedragonmc.games.firefighters.module.CustomItem
import com.bluedragonmc.games.firefighters.module.FireSpreadModule
import com.bluedragonmc.games.firefighters.module.add
import net.kyori.adventure.sound.Sound
import net.minestom.server.component.DataComponents
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent

/**
 * An item that starts fire where the player right clicks with it.
 * Must be right-clicking directly on a block.
 */
class FireStartingItem(override val itemId: String, itemStack: ItemStack) : CustomItem {

    val item: ItemStack = addIdentifier(itemStack)

    override fun onUseBlock(event: PlayerBlockInteractEvent) {
        super.onUseBlock(event)

        val player = event.player
        val damage = player.itemInMainHand.get(DataComponents.DAMAGE, 0)

        println(damage)

        if (damage > item.get(DataComponents.MAX_DAMAGE, Integer.MAX_VALUE)) {
            player.itemInMainHand = ItemStack.AIR
            player.playSound(Sound.sound(SoundEvent.ENTITY_ITEM_BREAK, Sound.Source.PLAYER, 1.0f, 1.0f))
            return
        } else {
            player.itemInMainHand = player.itemInMainHand.with(DataComponents.DAMAGE, damage + 1)
        }

        val direction = event.blockFace.toDirection()
        val blockPos = event.blockPosition.add(direction)
        if (event.instance.getBlock(
                blockPos, Block.Getter.Condition.TYPE
            ).isAir && FireSpreadModule.hasFullAdjacentFace(event.instance, blockPos)
        ) {
            event.instance.setBlock(blockPos, Block.FIRE)
        }
    }
}