package com.bluedragonmc.games.firefighters

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.instance.InstanceTimeModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.*
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import java.nio.file.Paths

class FirefightersGame(mapName: String) : Game("Firefighters", mapName) {
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
        use(VoteStartModule(minPlayers = 1, countdownSeconds = 5))
        use(WinModule())
    }
}