package com.bluedragonmc.example.server

import com.bluedragonmc.example.server.command.JoinCommand
import com.bluedragonmc.example.server.impl.DatabaseConnectionStub
import com.bluedragonmc.example.server.impl.EnvironmentStub
import com.bluedragonmc.example.server.impl.PermissionManagerStub
import com.bluedragonmc.example.server.impl.SingleGameQueue
import com.bluedragonmc.example.server.lobby.LobbyGame
import com.bluedragonmc.games.firefighters.FirefightersGame
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.IncomingRPCHandlerStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.tag.Tag
import net.minestom.server.world.biome.Biome
import net.minestom.server.world.biome.BiomeEffects

val GAME_CLASS = FirefightersGame::class
val LOBBY_CLASS = LobbyGame::class

const val GAME_NAME = "Firefighters"
/** The name of the map folder in worlds/gameName/ */
const val MAP_NAME = "Reactor"
const val LOBBY_MAP_NAME = "Lobby"
const val SERVER_ADDRESS = "0.0.0.0"
const val SERVER_PORT = 25565

fun main() {
    val server = MinecraftServer.init()
    MinecraftServer.getConnectionManager().setPlayerProvider(::CustomPlayer)
    MojangAuth.init()

    Permissions.initialize(PermissionManagerStub)
    Messaging.initializeIncoming(IncomingRPCHandlerStub())
    Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
    Database.initialize(DatabaseConnectionStub)
    Environment.setEnvironment(EnvironmentStub)
    GlobalTranslation.hook()

    MinecraftServer.getCommandManager().register(JoinCommand)

    // Create a lobby to use for the server's entire lifetime
    val lobby = LOBBY_CLASS.constructors.first().call("Lobby").apply {
        init()

        // Prevent the game library from automatically removing the spawning instance
        val tag = Tag.Long("instance_inactive_since")
        getInstance().setTag(tag, Long.MAX_VALUE)
    }

    MinecraftServer.getBiomeRegistry().register(
        "bluedragonmc:zombie",
        Biome.builder().effects(BiomeEffects.builder()
            .waterColor(Color(0x3F76E4))
            .waterFogColor(Color(0x50533))
            // ^ Same as plains
            .fogColor(Color(0x39ef97))
            .skyColor(NamedTextColor.GREEN).build()
            // ^ Modified
        ).build()
    )

    MinecraftServer.getGlobalEventHandler().let { eventHandler ->
        GlobalBlockHandlers.hook()
        GlobalChatFormat.hook(eventHandler)
        eventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = lobby.getInstance()
            event.player.displayName = Component.text(event.player.username, BRAND_COLOR_PRIMARY_1)
        }
    }

    server.start(SERVER_ADDRESS, SERVER_PORT)
}