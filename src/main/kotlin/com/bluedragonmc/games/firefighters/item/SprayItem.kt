package com.bluedragonmc.games.firefighters.item

import com.bluedragonmc.games.firefighters.module.CustomItem
import com.bluedragonmc.server.utils.toVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.utils.block.BlockIterator
import kotlin.random.Random

class SprayItem(override val item: ItemStack, private val sprayItemType: SprayItemType) : CustomItem {
    enum class SprayItemType(val particle: Particle) {
        FIRE_EXTINGUISH(Particle.CLOUD),
        FIRE_SPREAD(Particle.FLAME),
    }

    override fun onUseAir(event: PlayerUseItemEvent) {
        spray(event.instance, getEyePos(event.player), event.player.position.sub(event.player.previousPosition))
    }

    override fun onUseBlock(event: PlayerUseItemOnBlockEvent) {
        spray(event.instance, getEyePos(event.player), event.player.position.sub(event.player.previousPosition))
    }

    private fun spray(instance: Instance, startPos: Pos, playerVelocity: Point) {
        val lookDirection = startPos.direction()
        for (i in 0 .. SPRAY_PARTICLE_COUNT) {
            instance.sendGroupedPacket(
                ParticlePacket(
                    sprayItemType.particle,
                    startPos.add(lookDirection.mul(0.5)).add(getRandomOffset(0.2)), // spawn the particles 0.5 blocks in front of the player
                    lookDirection.add(playerVelocity).add(getRandomOffset(0.2)),
                    MAX_SPRAY_PARTICLE_SPEED,
                    0 // 0 particle count makes the particles directional for some reason???
                )
            )
        }

        // Raycast, placing or putting our fires in front of the player
        val targetBlockPos = getTargetBlockPosition(instance, startPos, MAX_SPRAY_DISTANCE) ?: return
        val targetBlock = instance.getBlock(targetBlockPos)
        when (sprayItemType) {
            SprayItemType.FIRE_EXTINGUISH -> {
                if (targetBlock.compare(Block.FIRE)) {
                    instance.setBlock(targetBlockPos, Block.AIR)
                }
            }
            SprayItemType.FIRE_SPREAD -> {
                if (targetBlock.compare(Block.FIRE)) return
                instance.setBlock(targetBlockPos, Block.FIRE) // TODO put it in the actual right spot
                // ^ you should set it to an air block
            }
        }
    }

    companion object {
        private const val MAX_SPRAY_DISTANCE = 6.0
        private const val MAX_SPRAY_PARTICLE_SPEED = 0.6f
        private const val SPRAY_PARTICLE_COUNT = 20

        fun getTargetBlockPosition(instance: Instance, startPos: Pos, maxDistance: Double): Point? {
            val it = BlockIterator(startPos.toVec(), startPos.direction(), 0.0, maxDistance)
            while (it.hasNext()) {
                val position = it.next()
                if (!instance.getBlock(position).isAir) return position
            }
            return null
        }

        fun getEyePos(player: Player) = player.position.add(0.0, player.eyeHeight, 0.0)
        fun getRandomOffset(max: Double) = Pos(Random.nextDouble(-max, max), Random.nextDouble(-max, max), Random.nextDouble(-max, max))
    }
}