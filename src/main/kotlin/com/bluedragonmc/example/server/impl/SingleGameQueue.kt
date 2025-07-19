package com.bluedragonmc.example.server.impl

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.example.server.GAME_CLASS
import com.bluedragonmc.example.server.GAME_NAME
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
 */
object SingleGameQueue : Queue() {
    init {
        MinecraftServer.getSchedulerManager().buildTask {
            val numAvailableGames =
                Game.games.count { game -> isValidGame(game) }
            if (numAvailableGames < 1) {
                newGameInstance()
            }
        }.repeat(Duration.ofSeconds(1)).schedule()
    }

    private fun isValidGame(game: Game) = game.name == GAME_NAME && (game.state == GameState.WAITING || game.state == GameState.STARTING || game.state == GameState.SERVER_STARTING)

    override fun getMaps(gameType: String): Array<File> = emptyArray()

    fun queue(player: Player) {
        val validGame = Game.games.firstOrNull { game -> game.name == GAME_NAME && game.state == GameState.WAITING }
        if (validGame == null) {
            MinecraftServer.getSchedulerManager().buildTask {
                queue(player)
            }.delay(Duration.ofSeconds(1)).schedule()
            return
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            player.sendMessage(
                Component.translatable(
                    "queue.added.game", NamedTextColor.DARK_GRAY, Component.text(validGame.name)
                )
            )
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