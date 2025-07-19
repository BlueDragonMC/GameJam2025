package com.bluedragonmc.example.server.lobby

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.DoubleJumpModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.MOTDModule
import com.bluedragonmc.server.module.minigame.PlayerResetModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.VoidDeathModule
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerSpawnEvent
import java.nio.file.Paths

class LobbyGame(mapName: String) : Game("Lobby", mapName) {
    override fun initialize() {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(ConfigModule())
        use(DoubleJumpModule())
        use(InstantRespawnModule())
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(MOTDModule(Component.text("Welcome to Firefighters!\nTo join a game, use /join.\nTo spectate, use /spectate."), showMapName = false))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SharedInstanceModule())
        use(SpawnpointModule(spawnpointProvider = SpawnpointModule.ConfigSpawnpointProvider()))
        use(VoidDeathModule(threshold = -64.0))
        use(WorldPermissionsModule())
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) event.player.teleport(getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player))
        }
    }
}