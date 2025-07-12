package com.bluedragonmc.example.server.impl

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.example.server.GAME_CLASS
import com.bluedragonmc.example.server.MAP_NAME
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.utils.GameState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.io.File
import java.time.Duration

/**
 * Extremely basic queue system that supports a single game type.
 * The queue allows players to join even after the game has started.
 */
object SingleGameQueue : Queue() {
    override fun getMaps(gameType: String): Array<File> = emptyArray()

    fun queue(player: Player) {
        val validGame = Game.games.firstOrNull { it.state != GameState.ENDING }
        if (validGame == null) {
            newGameInstance()
            MinecraftServer.getSchedulerManager().buildTask {
                queue(player)
            }.delay(Duration.ofSeconds(1)).schedule()
            return
        }
        if (validGame.state == GameState.SERVER_STARTING) {
            MinecraftServer.getSchedulerManager().buildTask {
                queue(player)
            }.delay(Duration.ofSeconds(1)).schedule()
            return
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            player.sendMessage(Component.translatable("queue.added.game", NamedTextColor.DARK_GRAY, Component.text(validGame.name)))
            validGame.addPlayer(player)
        }
    }

    fun newGameInstance() {
        GAME_CLASS.constructors.first().call(MAP_NAME).init()
    }

    /**
     * Queues the player for the game defined by [GAME_CLASS].
     * @param player the player to queue
     * @param gameType ignored
     */
    override fun queue(player: Player, gameType: CommonTypes.GameType) = queue(player)

    override fun randomMap(gameType: String): String? = getMaps(gameType).randomOrNull()?.name

    override fun start() {}
}