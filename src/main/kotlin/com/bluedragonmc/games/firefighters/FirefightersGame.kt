package com.bluedragonmc.games.firefighters

import com.bluedragonmc.games.firefighters.item.SprayItem
import com.bluedragonmc.games.firefighters.module.CustomItemsModule
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
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
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
        use(TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.TEAM_COUNT, autoTeamCount = 2, allowFriendlyFire = false))
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
            }
        }
    }

    private companion object {
        val FLAMETHROWER = SprayItem(ItemStack.of(Material.BLAZE_ROD), SprayItem.SprayItemType.FIRE_SPREAD)
        val EXTINGUISHER = SprayItem(ItemStack.of(Material.GLOW_INK_SAC), SprayItem.SprayItemType.FIRE_EXTINGUISH)
    }
}