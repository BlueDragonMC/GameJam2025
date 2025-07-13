package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.manage
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.block.Block
import java.time.Duration

class CutsceneModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {}

    fun playCutscene(player: Player, game: Game, points: List<Pos>, stepsPerCurve: Int = 5, tension: Float = 0.5f) {
        val oldGameMode = player.gameMode
        val splinePoints = generateSpline(points, stepsPerCurve, tension)
        val msPerPoint = 500L

        Entity(EntityType.BLOCK_DISPLAY).apply {
            val meta = entityMeta as BlockDisplayMeta
            meta.setBlockState(Block.DIAMOND_ORE)
            meta.posRotInterpolationDuration = msPerPoint.toInt() / 20
            setNoGravity(true)
            setInstance(player.instance, splinePoints.first())
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                player.gameMode = GameMode.SPECTATOR
                player.spectate(this)
            }
            for ((index, pos) in splinePoints.withIndex()) {
                MinecraftServer.getSchedulerManager().buildTask {
                    teleport(pos)
                }.delay(Duration.ofMillis(index * msPerPoint)).schedule().manage(game)
            }
            MinecraftServer.getSchedulerManager().buildTask {
                player.stopSpectating()
                remove()
                player.setGameMode(oldGameMode)
            }.delay(Duration.ofMillis((splinePoints.size + 1) * msPerPoint)).schedule().manage(game)
        }
    }

    private companion object {
        /**
         * Returns a list of points along a spline defined by the provided points.
         */
        fun generateSpline(points: List<Pos>, stepsPerCurve: Int, tension: Float): List<Pos> {
            val result = mutableListOf<Pos>()
            for (i in 0 until points.size - 1) {
                val prev = if (i == 0) points[i] else points[i - 1]
                val currStart = points[i]
                val currEnd = points[i + 1]
                val next = if (i == points.size - 2) points[i + 1] else points[i + 2]

                for (step in 0 .. stepsPerCurve) {
                    val t = step.toDouble() / stepsPerCurve
                    val tSquared = t * t
                    val tCubed = tSquared * t

                    val interpolatedPoint = prev.mul(-.5 * tension * tCubed + tension * tSquared - .5 * tension * t) // prev * tension * (-1/2t^3 + t^2 - 1/2t)
                        .add(currStart.mul(1 + .5 * tSquared * (tension - 6) + .5 * tCubed * (4 - tension)))
                        .add(currEnd.mul(.5 * tCubed * (tension - 4) + .5 * tension * t - (tension - 3) * tSquared))
                        .add(next.mul(-.5 * tension * tSquared + .5 * tension * tCubed))
                    result.add(interpolatedPoint)
                }
            }
            return result
        }
    }
}