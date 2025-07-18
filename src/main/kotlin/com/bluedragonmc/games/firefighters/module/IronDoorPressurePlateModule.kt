package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.registry.TagKey
import net.minestom.server.utils.Direction

/**
 * Allows players to open iron doors by standing on adjacent pressure plates
 */
class IronDoorPressurePlateModule : GameModule() {

    companion object {
        val pressurePlates = Block.staticRegistry().getTag(TagKey.ofHash("#minecraft:pressure_plates"))!!
        val doors = Block.staticRegistry().getTag(TagKey.ofHash("#minecraft:doors"))!!
    }

    private val openDoors = mutableListOf<BlockVec>()

    override fun initialize(
        parent: Game,
        eventNode: EventNode<Event>
    ) {

        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val instance = event.instance
            val pos = event.player.position

            val positionsToCheck = listOf(pos,pos.add(0.2, 0.0, 0.0), pos.add(-0.2, 0.0, 0.0), pos.add(0.0,0.0,0.2),pos.add(0.0,0.0,-0.2))

            for (pos in positionsToCheck) {
                if (pressurePlates.contains(instance.getBlock(pos))) {
                    // Player is standing on pressure plate; look for adjacent door
                    for (dir in Direction.entries) {
                        if (doors.contains(instance.getBlock(pos.add(dir)))) {
                            openDoor(instance, BlockVec(pos.add(dir)))
                        }
                    }
                }
            }
        }

        eventNode.addListener(InstanceTickEvent::class.java) { event ->
            val instance = event.instance
            openDoors.removeAll { door ->
                var found = false
                instance.entityTracker.nearbyEntities(door, 3.0, EntityTracker.Target.PLAYERS, { found = true })
                if (!found) {
                    instance.setBlock(door, instance.getBlock(door).withProperty("open", "false"))
                    return@removeAll true
                }
                return@removeAll false
            }
        }
    }

    private fun openDoor(instance: Instance, pos: BlockVec, exclude: List<Point> = emptyList()) {
        if (openDoors.contains(pos)) return
        instance.setBlock(pos, instance.getBlock(pos).withProperty("open", "true"))
        openDoors.add(pos)
        for (dir in Direction.entries) {
            val newPos = BlockVec(pos.add(dir))
            if (doors.contains(instance.getBlock(newPos)) && !exclude.contains(pos)) {
                openDoor(instance, newPos, exclude + newPos)
                break
            }
        }
    }
}