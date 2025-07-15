package com.bluedragonmc.games.firefighters

import com.bluedragonmc.games.firefighters.item.SprayItem
import com.bluedragonmc.games.firefighters.module.BurnableRegionsModule
import com.bluedragonmc.games.firefighters.module.CustomItemsModule
import com.bluedragonmc.games.firefighters.module.FireSpreadModule
import com.bluedragonmc.games.firefighters.module.add
import com.bluedragonmc.server.Game
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
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class FirefightersGame(mapName: String) : Game("Firefighters", mapName) {
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
        use(BurnableRegionsModule()) { regions ->
            val binding = getModule<SidebarModule>().bind { player ->
                if (state != GameState.INGAME) return@bind getStatusSection()
                val phase = (getInstance().worldAge % 20L) / 20.0
                val colors = "#a10000:#ea2300:#ff8100:#f25500:#d80000"
                val title = miniMessage.deserialize("<bold><gradient:$colors:${-phase}>BURN STATUS")
                listOf(getSpacer(), title) + regions.getScoreboardText() + getSpacer()
            }

            handleEvent<InstanceTickEvent> { event ->
                if (event.instance.worldAge % 3L == 0L) {
                    // Update scoreboard ~7x per second for the "Burn status" title animation
                    binding.update()
                }
            }
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