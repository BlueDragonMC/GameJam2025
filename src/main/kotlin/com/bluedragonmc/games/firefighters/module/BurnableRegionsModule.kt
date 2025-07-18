package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.games.firefighters.FirefightersGame
import com.bluedragonmc.games.firefighters.FlammableBlocks
import com.bluedragonmc.games.firefighters.rangeTo
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import com.bluedragonmc.server.utils.withTransition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.roundToInt

@DependsOn(TeamModule::class)
class BurnableRegionsModule(private val configKey: String) : GameModule() {

    private var regions: List<Region> = listOf()

    data class Region(val name: Component, val start: BlockVec, val end: BlockVec) {

        /**
         * The total number of flammable blocks in the region.
         * When the chunks contained within the region load, this is updated to the actual value.
         */
        var totalFlammableBlocks: Int? = null
        var currentFlammableBlocks: Int = 0

        fun countFlammableBlocks(instance: Instance) =
            (start..end).count { pos ->
                FlammableBlocks.canBurn(instance.getBlock(pos, Block.Getter.Condition.TYPE)!!)
            }

        // Called every second
        fun update(instance: Instance, parent: FirefightersGame) {
            try {
                val blocks = countFlammableBlocks(instance)

                if (totalFlammableBlocks == null) {
                    totalFlammableBlocks = blocks
                    for (block in (start..end)) {
                        if (FlammableBlocks.canBurn(instance.getBlock(block, Block.Getter.Condition.TYPE))) {
                            Entity(EntityType.SHULKER).apply {
                                eventNode().addListener(EntityTickEvent::class.java) {
                                    val currentBlock = instance.getBlock(block, Block.Getter.Condition.TYPE)
                                    if (currentBlock.compare(Block.FIRE) || currentBlock.isAir) {
                                        remove()
                                    }
                                    if (getProportionBurned() > 0.9) {
                                        remove()
                                    }
                                }
                                updateViewableRule { player ->
                                    parent.getModule<TeamModule>().getTeam(player) == parent.arsonistsTeam
                                }
                                setNoGravity(true)
                                isGlowing = true
                                isInvisible = true
                                setInstance(instance, block)
                                GlowingEntityUtils.glow(this, NamedTextColor.RED, instance.players)
                            }
                        }
                    }
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
            val start = child.node("start").get(Pos::class.java)!!
            val end = child.node("end").get(Pos::class.java)!!

            Region(name, BlockVec(start), BlockVec(end))
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
                regions.forEach { region -> region.update(event.instance, parent as FirefightersGame) }
            }
        }
    }

    fun getScoreboardText(): Collection<Component> {
        return regions.map { region ->
            val percent = region.getProportionBurned()
            region.name
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(
                    if (percent > 0.9) Component.text("\uD83D\uDCA3 GONE", NamedTextColor.DARK_RED)
                    else Component.text("${(percent * 100).roundToInt()}%").withTransition(
                        percent.toFloat(),
                        // Start with green at 0% and smoothly transition to dark red at 100%
                        NamedTextColor.GREEN,
                        NamedTextColor.YELLOW,
                        NamedTextColor.GOLD,
                        NamedTextColor.RED,
                        NamedTextColor.DARK_RED
                    ).decoration(TextDecoration.BOLD, percent > 0.75)
                        .append(Component.text(" burned", NamedTextColor.GRAY))
                )
        }
    }

    fun getFlammableBlocksRemaining(): Int {
        if (regions.any { it.totalFlammableBlocks == null }) return -1
        return regions.sumOf { it.currentFlammableBlocks }
    }

    fun getRegions(): List<Region> = regions
}