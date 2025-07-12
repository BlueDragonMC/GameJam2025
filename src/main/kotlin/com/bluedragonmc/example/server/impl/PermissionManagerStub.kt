package com.bluedragonmc.example.server.impl

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.api.PermissionManager
import com.bluedragonmc.server.api.PlayerMeta
import net.kyori.adventure.text.Component
import java.util.*

object PermissionManagerStub : PermissionManager {
    override fun getMetadata(player: UUID) = PlayerMeta(
        prefix = Component.empty(),
        suffix = Component.empty(),
        primaryGroup = "default",
        rankColor = ALT_COLOR_1,
    )

    override fun hasPermission(player: UUID, node: String) = true
}