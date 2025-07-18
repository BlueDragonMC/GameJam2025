package com.bluedragonmc.games.firefighters.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

// Quick and dirty implementation. Maybe I will add a better version of this to Common in the future.
class PowerupModule(
    private val powerups: Collection<Powerup>,
    private val spawnPositions: Collection<Pos>,
    private val spawnRate: Duration = Duration.ZERO, // TODO spawn powerups periodically
    private val maxSpawned: Int = spawnPositions.size,
    private val spawnAllOnStart: Boolean = false
) : GameModule() {
    private val spawnedPowerups = mutableListOf<SpawnedPowerup>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        require(maxSpawned <= spawnPositions.size) { "Not enough powerup spawn positions" }
        if (spawnAllOnStart) eventNode.addListener(GameStartEvent::class.java) { event ->
            repeat(maxSpawned) {
                spawnPowerup(powerups.random(), parent.getInstance())
            }
        }

        MinecraftServer.getSchedulerManager().buildTask {
            if (spawnedPowerups.size < maxSpawned) spawnPowerup(powerups.random(), parent.getInstance())
        }

        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.SPECTATOR) return@addListener
            spawnedPowerups.removeIf { powerup ->
                if (event.player.position.distanceSquared(powerup.itemDisplay.position) < 1.0) {
                    if (powerup.powerup.visibilityRule(event.player) && powerup.powerup.onPickup(event.player)) {
                        event.instance.playSound(Sound.sound(SoundEvent.ENTITY_ITEM_PICKUP, Sound.Source.PLAYER, 1.0f, 1.0f), event.player)
                        powerup.remove()
                        return@removeIf true
                    }
                }
                false
            }
        }
    }

    fun spawnPowerup(powerup: Powerup, instance: Instance) =
        spawnPowerup(powerup, instance, spawnPositions.filter { it !in spawnedPowerups.map { it.position } }.random())

    fun spawnPowerup(powerup: Powerup, instance: Instance, position: Pos) {
        spawnedPowerups.add(SpawnedPowerup(powerup, instance, position))
    }

    data class Powerup(
        val name: Component,
        val icon: Material,
        val visibilityRule: (Player) -> Boolean,
        val onPickup: (Player) -> Boolean,
        val glowColor: TextColor,
    )

    private data class SpawnedPowerup(
        val powerup: Powerup,
        val instance: Instance,
        val position: Pos
    ) {
        val hologram = Entity(EntityType.TEXT_DISPLAY).apply {
            updateViewableRule(powerup.visibilityRule)
            val meta = entityMeta as TextDisplayMeta
            meta.apply {
                text = powerup.name
                isHasNoGravity = true
                billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
            }
            setInstance(this@SpawnedPowerup.instance, this@SpawnedPowerup.position.add(0.0, 1.5, 0.0))
        }

        val itemDisplay = Entity(EntityType.ITEM_DISPLAY).apply {
            updateViewableRule(powerup.visibilityRule)
            val meta = entityMeta as ItemDisplayMeta
            meta.apply {
                itemStack = ItemStack.of(powerup.icon)
                isHasNoGravity = true
                scale = Vec(0.75, 0.75, 0.75)
                translation = Vec(0.0, 1.0, 0.0)
                isHasGlowingEffect = true
                glowColorOverride = powerup.glowColor.value()
            }
            eventNode().addListener(EntityTickEvent::class.java) { event ->
                val tickCount = event.entity.aliveTicks
                if (tickCount % 5L != 0L) return@addListener
                val t = ((tickCount / 10f) % 20) / 20f * 2 * Math.PI
                meta.apply {
                    rightRotation = floatArrayOf(0.0f, -cos(t).toFloat(), 0.0f, sin(t).toFloat())
                    translation = Vec(0.0, 1.0 + sin(t) / 10.0, 0.0)
                    transformationInterpolationDuration = 5
                    transformationInterpolationStartDelta = -1
                }
            }
            setInstance(this@SpawnedPowerup.instance, this@SpawnedPowerup.position)
        }

        fun remove() {
            hologram.remove()
            itemDisplay.remove()
        }
    }
}