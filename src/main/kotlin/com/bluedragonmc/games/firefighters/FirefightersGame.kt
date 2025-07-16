package com.bluedragonmc.games.firefighters

import com.bluedragonmc.games.firefighters.item.SprayItem
import com.bluedragonmc.games.firefighters.module.BurnableRegionsModule
import com.bluedragonmc.games.firefighters.module.CustomItemsModule
import com.bluedragonmc.games.firefighters.module.FireSpreadModule
import com.bluedragonmc.games.firefighters.module.add
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.instance.InstanceTimeModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.*
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.EventListener
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class FirefightersGame(mapName: String) : Game("Firefighters", mapName) {

    sealed interface Stage {

        /** Go to the next stage, returning the next stage */
        fun advance(parent: FirefightersGame): Stage

        /** The game hasn't started yet, no stages should be enabled */
        class BeforeGameStart : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                // Starting Stage 1
                parent.use(BurnableRegionsModule())
                parent.handleEvent(EventListener.builder(InstanceTickEvent::class.java).handler {
                    if ((parent.getModuleOrNull<BurnableRegionsModule>()?.getAverageBurnProportion() ?: 0.0) > 0.99) {
                        parent.currentStage = parent.currentStage.advance(parent)
                    }
                }.expireWhen { parent.currentStage !is Stage1 }.build())
                return Stage1()
            }
        }

        /** Arsonists trying to burn down the factories */
        class Stage1 : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                // Stage 1 -> Stage 2
                parent.unregister(parent.getModule<BurnableRegionsModule>())
                return Stage2()
            }
        }

        /** Arsonists trying to spread the fire to the nuclear plant */
        class Stage2 : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                // Stage 2 -> Stage 3
                return Stage3()
            }
        }

        /** Firefighters trying to escape */
        class Stage3 : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                // Ending the game
                return BeforeGameStart()
            }
        }
    }

    var currentStage: Stage = Stage.BeforeGameStart()

    override fun isInactive(): Boolean = false

    override fun initialize() {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(ConfigModule())
        use(DoorsModule())
        use(FallDamageModule())
        use(InstanceContainerModule())
        use(InstanceTimeModule())
        use(ItemDropModule())
        use(ItemPickupModule())
        use(MOTDModule(motd = Component.text("Firefighters vs arsonists")))
        use(NaturalRegenerationModule())
        use(OldCombatModule())
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SidebarModule(name))
        use(SpawnpointModule(SpawnpointModule.ConfigSpawnpointProvider()))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(
            TeamModule(
                autoTeams = true,
                autoTeamMode = TeamModule.AutoTeamMode.TEAM_COUNT,
                autoTeamCount = 2,
                allowFriendlyFire = false
            )
        )
        use(TimedRespawnModule(seconds = 5))
        use(VoidDeathModule(threshold = 0.0))
//        use(VoteStartModule(minPlayers = 1, countdownSeconds = 5))
        use(WinModule())

        use(CustomItemsModule()) {
            it.addCustomItem(FLAMETHROWER)
            it.addCustomItem(EXTINGUISHER)
        }

        handleEvent<PlayerJoinGameEvent> { event ->
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                event.player.inventory.addItemStack(FLAMETHROWER.item)
                event.player.inventory.addItemStack(EXTINGUISHER.item)
                event.player.inventory.addItemStack(ItemStack.of(Material.FLINT_AND_STEEL))
            }
        }

        handleEvent<PlayerBlockInteractEvent> { event ->
            if (event.isBlockingItemUse) return@handleEvent
            if (event.player.getItemInHand(event.hand).material() == Material.FLINT_AND_STEEL) {
                val direction = event.blockFace.toDirection()
                val blockPos = event.blockPosition.add(direction)
                if (event.instance.getBlock(
                        blockPos, Block.Getter.Condition.TYPE
                    ).isAir && FireSpreadModule.hasFullAdjacentFace(event.instance, blockPos)
                ) {
                    event.instance.setBlock(blockPos, Block.FIRE)
                }
            }
        }

        use(FireSpreadModule())

        val binding = getModule<SidebarModule>().bind { player ->
            if (state != GameState.INGAME) return@bind getStatusSection()
            val list = getStatusSection().toMutableList()

            if (hasModule<BurnableRegionsModule>()) { // Stage 1
                val regions = getModule<BurnableRegionsModule>()
                val phase = (getInstance().worldAge % 20L) / 20.0
                val colors = "#a10000:#ea2300:#ff8100:#f25500:#d80000"
                val title = miniMessage.deserialize("<bold><gradient:$colors:${-phase}>BURN STATUS")
                list += getSpacer()
                list += title
                list += regions.getScoreboardText()
                list += getSpacer()
            }

            return@bind list
        }

        handleEvent<InstanceTickEvent> { event ->
            if (event.instance.worldAge % 3L == 0L) {
                // Update scoreboard ~7x per second for the "Burn status" title animation
                binding.update()
            }
        }

        onGameStart {
            if (currentStage is Stage.BeforeGameStart) { // sanity check, should always be true
                currentStage = currentStage.advance(this)
            } else {
                currentStage = Stage.Stage1()
            }
        }

        // TODO for testing only below this line
        getInstance().apply {
            setBlock(-7, 64, 0, Block.COAL_BLOCK)
            setBlock(-7, 65, 0, Block.COAL_BLOCK)
            setBlock(-7, 66, 0, Block.COAL_BLOCK)
        }

        handleEvent<PlayerJoinGameEvent> {
            EventDispatcher.call(GameStartEvent(this))
            state = GameState.INGAME
        }
    }

    private companion object {
        val FLAMETHROWER = SprayItem(
            "flamethrower",
            ItemStack.builder(Material.BLAZE_ROD)
                .customName(Component.translatable("item.flamethrower", NamedTextColor.DARK_RED).noItalic())
                .set(DataComponents.MAX_DAMAGE, 64).build(),
            SprayItem.SprayItemType.FIRE_SPREAD
        )
        val EXTINGUISHER = SprayItem(
            "extinguisher",
            ItemStack.builder(Material.GLOW_INK_SAC)
                .customName(Component.translatable("item.extinguisher", NamedTextColor.DARK_AQUA).noItalic())
                .set(DataComponents.MAX_DAMAGE, 64).build(),
            SprayItem.SprayItemType.FIRE_EXTINGUISH
        )
    }
}