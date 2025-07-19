package com.bluedragonmc.games.firefighters.item

import com.bluedragonmc.games.firefighters.module.CustomItem
import com.bluedragonmc.games.firefighters.module.add
import com.bluedragonmc.games.firefighters.rangeTo
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent

class WaterBucketItem(override val itemId: String, itemStack: ItemStack) : CustomItem {

    val item = addIdentifier(itemStack)

    override fun onUseBlock(event: PlayerBlockInteractEvent) {
        super.onUseBlock(event)
        val player = event.player
        player.itemInMainHand = damageItem(player.itemInMainHand, player, 1)

        if (player.fireTicks > 0) {
            player.fireTicks = 0
            event.instance.playSound(
                Sound.sound(SoundEvent.BLOCK_FIRE_EXTINGUISH, Sound.Source.BLOCK, 1.0f, 1.0f), player
            )
        }

        val block = event.blockPosition.add(event.blockFace.oppositeFace.toDirection())
        for (pos in (BlockVec(block.sub(3.0, 3.0, 3.0))..BlockVec(block.add(3.0, 3.0, 3.0)))) {
            if (event.instance.getBlock(pos).compare(Block.FIRE)) {
                event.instance.setBlock(pos, Block.AIR)
                event.instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_FIRE_EXTINGUISH, Sound.Source.BLOCK, 1.0f, 1.0f), pos
                )
                event.instance.sendGroupedPacket(
                    ParticlePacket(
                        Particle.DRIPPING_WATER, pos.add(0.5, 0.5, 0.5), Vec(0.25, 0.25, 0.25), 0.5f, 5
                    )
                )
            }
        }
    }
}