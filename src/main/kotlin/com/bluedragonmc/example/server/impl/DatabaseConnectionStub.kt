package com.bluedragonmc.example.server.impl

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.DatabaseConnection
import com.bluedragonmc.server.model.GameDocument
import com.bluedragonmc.server.model.PlayerDocument
import net.minestom.server.entity.Player
import java.util.*
import kotlin.reflect.KMutableProperty

object DatabaseConnectionStub : DatabaseConnection {
    override suspend fun getPlayerDocument(username: String): PlayerDocument? {
        TODO("Not yet implemented")
    }

    override suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? {
        TODO("Not yet implemented")
    }

    override suspend fun getPlayerDocument(player: Player): PlayerDocument {
        TODO("Not yet implemented")
    }

    override suspend fun getPlayerForPunishmentId(id: String): PlayerDocument? {
        TODO("Not yet implemented")
    }

    override suspend fun loadDataDocument(player: CustomPlayer) {
        TODO("Not yet implemented")
    }

    override suspend fun logGame(game: GameDocument) {
        TODO("Not yet implemented")
    }

    override suspend fun rankPlayersByStatistic(key: String, sortCriteria: String, limit: Int): List<PlayerDocument> {
        TODO("Not yet implemented")
    }

    override suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T) {
        TODO("Not yet implemented")
    }
}