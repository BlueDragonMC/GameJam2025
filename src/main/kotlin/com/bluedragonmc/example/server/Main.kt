package com.bluedragonmc.example.server

import com.bluedragonmc.example.server.impl.DatabaseConnectionStub
import com.bluedragonmc.example.server.impl.EnvironmentStub
import com.bluedragonmc.example.server.impl.PermissionManagerStub
import com.bluedragonmc.example.server.impl.SingleGameQueue
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
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.tag.Tag

val GAME_CLASS = FirefightersGame::class
/** The name of the map folder in worlds/gameName/ */
const val MAP_NAME = "Reactor"
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

    // Create a game so it's ready as soon as the server starts
    SingleGameQueue.newGameInstance()

    // Create an instance for players to spawn in even when the game is not loaded
    val spawningInstance = MinecraftServer.getInstanceManager().createInstanceContainer()
    // Prevent the game library from automatically removing the spawning instance
    val tag = Tag.Long("instance_inactive_since")
    spawningInstance.setTag(tag, Long.MAX_VALUE)

    MinecraftServer.getGlobalEventHandler().let { eventHandler ->
        GlobalChatFormat.hook(eventHandler)
        eventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = spawningInstance
            event.player.displayName = Component.text(event.player.username, BRAND_COLOR_PRIMARY_1)
        }
        eventHandler.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) SingleGameQueue.queue(event.player)
        }
    }

    server.start(SERVER_ADDRESS, SERVER_PORT)
}