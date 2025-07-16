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
import kotlin.random.Random

fun Point.add(direction: Direction) = add(direction.toPoint())
fun Direction.toPoint(): Point = BlockVec(normalX(), normalY(), normalZ())

// TODO: don't allow blocks in Stage 2 regions to burn while Stage 1 is active

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

        fun getProperties(instance: Instance, pos: Point): Map<String,String>{
            val properties = mutableMapOf<String,String>()
            for (direction in Direction.entries) {
                if (instance.getBlock(pos.add(direction)).registry().collisionShape()
                        .isFaceFull(BlockFace.fromDirection(direction.opposite()))
                ) {
                    if (direction == Direction.DOWN) return emptyMap() // Fire supported from below never connects to other directions
                    properties.put(direction.name.lowercase(), "true")
                }
            }
            return properties
        }

        fun setFire(instance: Instance, pos: Point): Boolean {
            val properties = getProperties(instance, pos)

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

            // If this fire has been alive for too long, remove it
            if (aliveTicks++ >= FIRE_SPREAD_MAX_TIME) {
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    // ^ We need to run this at the start of the next tick so that we're not mutating the tickable block handlers as they're being iterated over (see DynamicChunk#tick)
                    tick.instance.setBlock(tick.blockPosition, Block.AIR)
                }
                return
            }

            val hasSupportBelow = tick.instance.getBlock(tick.blockPosition.add(0.0, -1.0, 0.0)).registry().collisionShape().isFaceFull(BlockFace.TOP)

            if (!hasSupportBelow || aliveTicks >= FIRE_SPREAD_MAX_TIME / 3) {
                // If this fire block doesn't have any adjacent flammable blocks, remove it
                var hasAdjacentFlammableBlock = false

                for (adjacentPos in iterateAdjacentBlocks(tick.blockPosition)) {
                    if (FlammableBlocks.isFlammable(tick.instance.getBlock(adjacentPos, Block.Getter.Condition.TYPE))) {
                        hasAdjacentFlammableBlock = true
                    }
                }

                if (!hasAdjacentFlammableBlock) {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        // ^ We need to run this at the start of the next tick so that we're not mutating the tickable block handlers as they're being iterated over (see DynamicChunk#tick)
                        tick.instance.setBlock(tick.blockPosition, Block.AIR)
                    }
                    return
                }
            }

            val props = tick.block.properties()
            val newProps = getProperties(tick.instance, tick.blockPosition)
            if (newProps.any { (key, value) -> props[key] != value }) {
                tick.instance.setBlock(tick.blockPosition, tick.block.withProperties(newProps))
            }

            // Try to spread this fire to other blocks
            spread@{
                val chance = FlammableBlocks.getSpreadChance(tick.block) ?: return@spread

                if (Random.nextDouble() > BASE_FIRE_SPREAD_CHANCE * chance) {
                    return@spread
                }

                for (adjacentPos in iterateAdjacentBlocks(tick.blockPosition)) {
                    if (tick.instance.getBlock(adjacentPos, Block.Getter.Condition.TYPE).isAir &&
                        hasFullAdjacentFace(tick.instance, adjacentPos) &&
                        FlammableBlocks.isFlammable(
                            tick.instance.getBlock(
                                tick.blockPosition,
                                Block.Getter.Condition.TYPE
                            )
                        )
                    ) {
                        MinecraftServer.getSchedulerManager().scheduleNextTick {
                            // ^ We need to run this at the start of the next tick so that we're not mutating the tickable block handlers as they're being iterated over (see DynamicChunk#tick)
                            setFire(tick.instance, adjacentPos)
                        }
                        break
                    }
                }
            }

            // Try to burn (destroy) adjacent flammable blocks
            for (adjacentPos in iterateAdjacentBlocks(tick.blockPosition)) {
                val block = tick.instance.getBlock(adjacentPos, Block.Getter.Condition.TYPE)
                val chance = FlammableBlocks.getBurnChance(block) ?: continue
                if (Random.nextDouble() < BASE_BURN_CHANCE * chance) {
                    setFire(tick.instance, adjacentPos)
                }
            }
        }

        private fun iterateAdjacentBlocks(pos: Point): Iterator<Point> {
            val seed = (pos.x() + pos.y() + pos.z()).toInt() % FIRE_SPREAD_DIRECTIONS.size
            val iterator = FIRE_SPREAD_DIRECTIONS.iterateStartingAt(seed)
            return object : Iterator<Point> {
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun next(): Point = pos.add(iterator.next())
            }
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
