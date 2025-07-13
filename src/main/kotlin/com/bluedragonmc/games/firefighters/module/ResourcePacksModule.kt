package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.resource.ResourcePackStatus
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.*

/**
 * Automatically sends players a resource pack when they join the game,
 * and removes it when they leave the game.
 */
class ResourcePacksModule(
    firstResourcePack: ResourcePackInfo,
    otherResourcePacks: Array<ResourcePackInfo> = arrayOf(),
    prompt: Component = Component.translatable("multiplayer.texturePrompt.line1"),
    required: Boolean = true,
) : GameModule() {
    private lateinit var parent: Game
    private val request = ResourcePackRequest.resourcePackRequest()
        .packs(firstResourcePack, *otherResourcePacks)
        .prompt(prompt)
        .required(required)
        .callback { uuid, status, audience ->
            parent.callEvent(FinishLoadResourcePackEvent(parent, uuid, status, audience))
        }
        .build()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerJoinGameEvent::class.java) { event ->
            event.player.sendResourcePacks(request)
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            event.player.removeResourcePacks(request)
        }
    }

    class FinishLoadResourcePackEvent(
        game: Game,
        val uuid: UUID,
        val resourcePackStatus: ResourcePackStatus,
        val audience: Audience,
    ) : GameEvent(game)
}