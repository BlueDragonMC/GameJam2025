package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.key.Key
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
            private const val FIRE_SPREAD_CHANCE = 0.005 // Chance that a fire block will spread every tick
            private const val FIRE_SPREAD_TRIES =
                50 // The amount of times that each fire block tries to spread before giving up permanently

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

        private var tries = 0

        override fun tick(tick: BlockHandler.Tick) {
            super.tick(tick)

            if (tries >= FIRE_SPREAD_TRIES) {
                tick.instance.setBlock(
                    tick.blockPosition,
                    tick.block.withHandler(BlockHandler.Dummy.get(KEY.asString()))
                )
                return
            }

            if (Random.nextDouble() < FIRE_SPREAD_CHANCE) {
                tries++
                val adjacentPos = findAdjacentBlock(tick.instance, tick.blockPosition) ?: return
                setFire(tick.instance, adjacentPos)
            }
        }

        private fun findAdjacentBlock(instance: Instance, pos: Point): Point? {
            val seed = (pos.x() + pos.y() + pos.z()).toInt() % FIRE_SPREAD_DIRECTIONS.size
            for (direction in FIRE_SPREAD_DIRECTIONS.iterateStartingAt(seed)) {
                val adjacentPos = pos.add(direction)
                if (instance.getBlock(adjacentPos, Block.Getter.Condition.TYPE).isAir &&
                    hasFullAdjacentFace(instance, adjacentPos)
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
