package com.bluedragonmc.games.firefighters.item

import com.bluedragonmc.games.firefighters.module.CustomItem
import com.bluedragonmc.games.firefighters.module.FireSpreadModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.utils.toVec
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.block.BlockIterator
import java.time.Duration
import kotlin.random.Random

class SprayItem(override val itemId: String, item: ItemStack, private val sprayItemType: SprayItemType) :
    CustomItem {

    val item = addIdentifier(item)

    enum class SprayItemType(val particle: Particle) {
        FIRE_EXTINGUISH(Particle.CLOUD),
        FIRE_SPREAD(Particle.FLAME),
    }

    override fun onUseAir(event: PlayerUseItemEvent) {
        spray(event.player)

        var point = event.player.position
        repeat(16) { i ->
            point = point.add(event.player.position.direction().div(4.0))
            event.instance.entityTracker.nearbyEntities(point.add(0.0, -1.0, 0.0), 0.1 + 0.04 * i, EntityTracker.Target.ENTITIES) { target ->
                if (target == event.player || target !is LivingEntity) return@nearbyEntities
                val kb = event.player.position.direction()
                OldCombatModule.takeKnockback(-kb.x, -kb.z, target, 0.2)
            }
        }
    }

    override fun onUseBlock(event: PlayerBlockInteractEvent) {
        spray(event.player)
    }

    private fun spray(player: Player) {
        player.itemInMainHand = damageItem(player.itemInMainHand, player, SPRAYS_PER_ACTION)

        repeat(SPRAYS_PER_ACTION) { i ->
            MinecraftServer.getSchedulerManager().buildTask {
                val instance = player.instance
                val startPos = getEyePos(player)
                val playerVelocity = player.position.sub(player.previousPosition)
                val lookDirection = startPos.direction()
                repeat(SPRAY_PARTICLE_COUNT) {
                    instance.sendGroupedPacket(
                        ParticlePacket(
                            sprayItemType.particle,
                            startPos.add(lookDirection.mul(PARTICLE_POSITION_OFFSET))
                                .add(getRandomOffset(PARTICLE_POSITION_VARIATION)), // spawn the particles 0.5 blocks in front of the player
                            lookDirection.add(playerVelocity).add(getRandomOffset(PARTICLE_VELOCITY_VARIATION)),
                            MAX_SPRAY_PARTICLE_SPEED,
                            0 // 0 particle count makes the particles directional for some reason???
                        )
                    )
                }

                // Raycast, placing or putting out fires in front of the player
                val (closestAirBlockPos, targetBlockPos) = getTargetBlockPosition(
                    instance,
                    startPos,
                    MAX_SPRAY_DISTANCE
                )
                    ?: return@buildTask
                val targetBlock = instance.getBlock(targetBlockPos, Block.Getter.Condition.TYPE)
                when (sprayItemType) {
                    SprayItemType.FIRE_EXTINGUISH -> {
                        instance.playSound(Sound.sound(SoundEvent.BLOCK_FIRE_EXTINGUISH, Sound.Source.BLOCK, 0.5f, 1.0f), player)
                        if (targetBlock.compare(Block.FIRE)) {
                            instance.setBlock(targetBlockPos, Block.AIR)
                        }
                    }

                    SprayItemType.FIRE_SPREAD -> {
                        instance.playSound(Sound.sound(SoundEvent.ENTITY_BLAZE_SHOOT, Sound.Source.BLOCK, 0.5f, 1.0f), player)
                        if (targetBlock.compare(Block.FIRE)) return@buildTask
                        if (!FireSpreadModule.hasFullAdjacentFace(
                                instance,
                                closestAirBlockPos ?: return@buildTask
                            )
                        ) return@buildTask
                        instance.setBlock(closestAirBlockPos, Block.FIRE)
                    }
                }
            }.delay(Duration.ofMillis(MILLIS_BETWEEN_SPRAYS * i)).schedule()
        }
    }

    companion object {
        /** The max distance from the player for a block to be affected by spray */
        private const val MAX_SPRAY_DISTANCE = 6.0
        private const val MAX_SPRAY_PARTICLE_SPEED = 0.6f

        /** How many particles to spawn for each spray */
        private const val SPRAY_PARTICLE_COUNT = 8

        /** How many times to release the spray each time you right click */
        private const val SPRAYS_PER_ACTION = 4

        /** The duration between sprays in a single right click */
        private const val MILLIS_BETWEEN_SPRAYS = 75L

        /** Random variation in the spawn positions of each particle */
        private const val PARTICLE_POSITION_VARIATION = 0.2

        /** How far in front of the player to spawn the particles */
        private const val PARTICLE_POSITION_OFFSET = 0.8

        /** Random variation in the direction the particles go */
        private const val PARTICLE_VELOCITY_VARIATION = 0.2

        fun getTargetBlockPosition(instance: Instance, startPos: Pos, maxDistance: Double): Pair<Point?, Point>? {
            val it = BlockIterator(startPos.toVec(), startPos.direction(), 0.0, maxDistance)
            var prev: Point? = null
            while (it.hasNext()) {
                val position = it.next()
                if (!instance.getBlock(position).isAir) return prev to position
                prev = position
            }
            return null
        }

        fun getEyePos(player: Player) = player.position.add(0.0, player.eyeHeight, 0.0)
        fun getRandomOffset(max: Double) =
            Pos(Random.nextDouble(-max, max), Random.nextDouble(-max, max), Random.nextDouble(-max, max))
    }
}