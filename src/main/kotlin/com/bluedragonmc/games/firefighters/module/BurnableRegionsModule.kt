package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.games.firefighters.FlammableBlocks
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.utils.withTransition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BurnableRegionsModule(private val configKey: String) : GameModule() {

    private var regions: List<Region> = listOf()

    private data class Region(val name: Component, val minimum: BlockVec, val maximum: BlockVec) {

        /**
         * The total number of flammable blocks in the region.
         * When the chunks contained within the region load, this is updated to the actual value.
         */
        var totalFlammableBlocks: Int? = null
        var currentFlammableBlocks: Int = 0

        fun countFlammableBlocks(instance: Instance) =
            (minimum.blockX()..maximum.blockX()).sumOf { x ->
                (minimum.blockY()..maximum.blockY()).sumOf { y ->
                    (minimum.blockZ()..maximum.blockZ()).count { z ->
                        FlammableBlocks.isFlammable(instance.getBlock(x, y, z, Block.Getter.Condition.TYPE)!!)
                    }
                }
            }

        // Called every second
        fun update(instance: Instance) {
            try {
                val blocks = countFlammableBlocks(instance)

                if (totalFlammableBlocks == null) {
                    totalFlammableBlocks = blocks
                }
                currentFlammableBlocks = blocks
            } catch (e: NullPointerException) {
                if (e.message?.startsWith("Unloaded chunk at ") == true) { // A chunk in the region isn't loaded; don't start counting yet
                    return
                }
            }
        }

        fun getProportionBurned(): Double {
            if (totalFlammableBlocks == null) {
                return 0.0
            }
            if (totalFlammableBlocks == 0) {
                return 1.0
            }
            return 1.0 - (currentFlammableBlocks.toDouble() / totalFlammableBlocks!!.toDouble())
        }
    }

    fun loadFrom(parent: Game, configKey: String) {
        regions = parent.getModule<ConfigModule>().getConfig().node(configKey).childrenList().map { child ->
            val name = child.node("name").get(Component::class.java)!!
            val pos1 = child.node("start").get(Pos::class.java)!!
            val pos2 = child.node("end").get(Pos::class.java)!!

            val minimum = BlockVec(
                min(pos1.blockX(), pos2.blockX()),
                min(pos1.blockY(), pos2.blockY()),
                min(pos1.blockZ(), pos2.blockZ())
            )
            val maximum = BlockVec(
                max(pos1.blockX(), pos2.blockX()),
                max(pos1.blockY(), pos2.blockY()),
                max(pos1.blockZ(), pos2.blockZ())
            )

            Region(name, minimum, maximum)
        }
    }

    override fun initialize(
        parent: Game,
        eventNode: EventNode<Event>
    ) {
        loadFrom(parent, configKey)

        eventNode.addListener(InstanceTickEvent::class.java) { event ->
            if (event.instance.worldAge % 20L == 0L) {
                // Every second, update every region's flammable block count
                regions.forEach { region -> region.update(event.instance) }
            }
        }
    }

    fun getScoreboardText(): Collection<Component> {
        return regions.map { region ->
            val percent = region.getProportionBurned()
            region.name
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(
                    Component.text("${(percent * 100).roundToInt()}%").withTransition(
                        percent.toFloat(),
                        // Start with green at 0% and smoothly transition to dark red at 100%
                        NamedTextColor.GREEN,
                        NamedTextColor.YELLOW,
                        NamedTextColor.GOLD,
                        NamedTextColor.RED,
                        NamedTextColor.DARK_RED
                    ).decoration(TextDecoration.BOLD, percent > 0.75)
                )
                .append(Component.text(" burned", NamedTextColor.GRAY))
        }
    }

    fun getFlammableBlocksRemaining(): Int {
        if (regions.any { it.totalFlammableBlocks == null }) return -1
        return regions.sumOf { it.currentFlammableBlocks }
    }
}