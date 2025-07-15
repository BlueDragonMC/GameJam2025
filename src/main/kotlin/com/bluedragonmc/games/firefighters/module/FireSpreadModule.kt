package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.games.firefighters.FlammableBlocks
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceBlockUpdateEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.utils.Direction
import java.util.function.Predicate
import kotlin.random.Random

fun Point.add(direction: Direction) = add(direction.toPoint())
fun Direction.toPoint(): Point = BlockVec(normalX(), normalY(), normalZ())

class FireSpreadModule : GameModule() {

    override fun initialize(
        parent: Game,
        eventNode: EventNode<Event>
    ) {
        eventNode.addListener(InstanceBlockUpdateEvent::class.java) { event ->
            if (event.block.compare(Block.FIRE) && event.block.handler() == null) {
                setFire(event.instance, event.blockPosition)
            }
        }
    }

    companion object {
        fun setFire(instance: Instance, pos: Point): Boolean {
            val properties = mutableMapOf<String, String>()

            // Show fire on adjacent blocks if the block below won't fully connect with the fire below
            if (!instance.getBlock(pos.sub(0.0, 1.0, 0.0)).registry().collisionShape().isFaceFull(BlockFace.TOP)) {
                for (direction in Direction.entries) {
                    if (direction == Direction.DOWN) continue
                    if (instance.getBlock(pos.add(direction)).registry().collisionShape()
                            .isFaceFull(BlockFace.fromDirection(direction.opposite()))
                    ) {
                        properties.put(direction.name.lowercase(), "true")
                    }
                }

                if (properties.isEmpty()) {
                    // Fire which isn't connected to any blocks (i.e. just floating in the air) shouldn't be placed
                    return false
                }
            }

            instance.setBlock(
                pos, Block.FIRE
                    .withProperties(properties)
                    .withHandler(FireBlockHandler())
            )

            return true
        }

        fun hasFullAdjacentFace(instance: Instance, pos: Point): Boolean {
            for (direction in Direction.entries) {
                if (instance.getBlock(pos.add(direction), Block.Getter.Condition.TYPE).registry().collisionShape()
                        .isFaceFull(BlockFace.fromDirection(direction.opposite()))
                ) {
                    return true
                }
            }
            return false
        }
    }

    class FireBlockHandler : BlockHandler {
        companion object {
            private val KEY = Key.key("minecraft:fire")

            /**
             * The base chance for a fire block to spread every tick, multiplied by the block's spread chance in [FlammableBlocks]
             */
            private const val BASE_FIRE_SPREAD_CHANCE = 0.0005 // Chance that a fire block will spread every tick

            /**
             * The maximum amount of ticks that fire will attempt to spread before burning out
             */
            private val FIRE_SPREAD_MAX_TIME = ServerFlag.SERVER_TICKS_PER_SECOND * 15

            /**
             * The base chance for a fire block to burn out every tick, multiplied by the block's burn chance in [FlammableBlocks]
             */
            private const val BASE_BURN_CHANCE = 0.0005

            private val FIRE_SPREAD_DIRECTIONS by lazy {
                val directions = mutableListOf<Point>()

                for (x in -1..1) {
                    for (y in -1..1) {
                        for (z in -1..1) {
                            if (x == 0 && (y == -1 || y == 0) && z == 0) continue // Ignore the current block & 1 block below
                            directions.add(BlockVec(x, y, z))
                        }
                    }
                }

                return@lazy directions.toTypedArray()
            }
        }

        override fun getKey(): Key = KEY

        private var aliveTicks = 0

        override fun tick(tick: BlockHandler.Tick) {
            super.tick(tick)

            if (aliveTicks++ >= FIRE_SPREAD_MAX_TIME) {
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    // ^ We need to run this at the start of the next tick so that we're not mutating the tickable block handlers as they're being iterated over (see DynamicChunk#tick)
                    tick.instance.setBlock(tick.blockPosition, Block.AIR)
                }
                return
            }

            spread@{
                val chance = FlammableBlocks.getSpreadChance(tick.block) ?: return@spread

                if (Random.nextDouble() < BASE_FIRE_SPREAD_CHANCE * chance) {
                    val adjacentPos = findAdjacentBlock(tick.instance, tick.blockPosition) { pos ->
                        FlammableBlocks.isFlammable(tick.instance.getBlock(pos, Block.Getter.Condition.TYPE))
                    } ?: return@spread

                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        // ^ We need to run this at the start of the next tick so that we're not mutating the tickable block handlers as they're being iterated over (see DynamicChunk#tick)
                        setFire(tick.instance, adjacentPos)
                    }
                }
            }

            burn@{
                val chance = FlammableBlocks.getBurnChance(tick.block) ?: return@burn

                if (Random.nextDouble() < BASE_BURN_CHANCE * chance) {
                    val adjacentPos = findAdjacentBlock(tick.instance, tick.blockPosition) { pos ->
                        FlammableBlocks.isFlammable(tick.instance.getBlock(pos, Block.Getter.Condition.TYPE))
                    } ?: return@burn

                    setFire(tick.instance, adjacentPos)
                }
            }
        }

        private fun findAdjacentBlock(
            instance: Instance,
            pos: Point,
            predicate: Predicate<Point> = Predicate { true }
        ): Point? {
            val seed = (pos.x() + pos.y() + pos.z()).toInt() % FIRE_SPREAD_DIRECTIONS.size
            for (direction in FIRE_SPREAD_DIRECTIONS.iterateStartingAt(seed)) {
                val adjacentPos = pos.add(direction)
                if (instance.getBlock(adjacentPos, Block.Getter.Condition.TYPE).isAir &&
                    hasFullAdjacentFace(instance, adjacentPos) &&
                    predicate.test(adjacentPos)
                ) {
                    return adjacentPos
                }
            }
            return null
        }

        override fun isTickable() = true
    }
}

private fun <T> Array<T>.iterateStartingAt(start: Int): Iterator<T> {

    if (start !in 0..<size) {
        throw ArrayIndexOutOfBoundsException()
    }

    var current = start
    var hitStart = false
    var finished = false

    return object : Iterator<T> {
        override fun next(): T {
            val item = this@iterateStartingAt[current]
            current++
            if (current == size) {
                current = 0
            }
            return item
        }

        override fun hasNext(): Boolean {
            if (current == start) {
                if (!hitStart) {
                    hitStart = true
                } else {
                    finished = true
                    return false
                }
            }
            return !finished
        }
    }
}
