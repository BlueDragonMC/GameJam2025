package com.bluedragonmc.example.server.impl

import com.bluedragonmc.example.server.GAME_CLASS
import com.bluedragonmc.server.VersionInfo
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue

import java.net.InetAddress

object EnvironmentStub : Environment() {
    override val gameClasses: Collection<String> = listOf(GAME_CLASS.qualifiedName!!)
    override val grpcServerPort: Int
        get() = TODO("Not yet implemented")
    override val isDev: Boolean = false
    override val luckPermsHostname: String
        get() = TODO("Not yet implemented")
    override val mongoConnectionString: String
        get() = TODO("Not yet implemented")
    override val puffinHostname: String
        get() = TODO("Not yet implemented")
    override val puffinPort: Int
        get() = TODO("Not yet implemented")
    override val queue: Queue = SingleGameQueue
    override val versionInfo: VersionInfo = VersionInfoStub

    override suspend fun getServerName(): String = InetAddress.getLocalHost().hostName

    private object VersionInfoStub : VersionInfo {
        override val BRANCH: String? = null
        override val COMMIT: String? = null
        override val COMMIT_DATE: String? = null
    }

}