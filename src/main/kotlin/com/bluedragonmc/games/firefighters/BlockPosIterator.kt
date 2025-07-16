package com.bluedragonmc.games.firefighters

import net.minestom.server.coordinate.BlockVec
import kotlin.math.max
import kotlin.math.min

class BlockPosIterable(private val start: BlockVec, private val end: BlockVec) : Iterable<BlockVec> {
    override fun iterator() = BlockPosIterator(start, end)
}

class BlockPosIterator(
    start: BlockVec,
    end: BlockVec
) : Iterator<BlockVec> {
    private val minimum = BlockVec(
        min(start.blockX(), end.blockX()),
        min(start.blockY(), end.blockY()),
        min(start.blockZ(), end.blockZ())
    )
    private val maximum = BlockVec(
        max(start.blockX(), end.blockX()),
        max(start.blockY(), end.blockY()),
        max(start.blockZ(), end.blockZ())
    )

    private var currentX = minimum.blockX()
    private var currentY = minimum.blockY()
    private var currentZ = minimum.blockZ()

    private var done = false

    override fun hasNext() = !done

    override fun next(): BlockVec {
        if (currentZ < maximum.blockZ()) {
            currentZ++
        } else if (currentY < maximum.blockY()) {
            currentZ = minimum.blockZ()
            currentY++
        } else if (currentX < maximum.blockX()) {
            currentZ = minimum.blockZ()
            currentY = minimum.blockY()
            currentX++
        } else {
            done = true
        }

        return BlockVec(currentX, currentY, currentZ)
    }
}

operator fun BlockVec.rangeTo(end: BlockVec) = BlockPosIterable(this, end)
