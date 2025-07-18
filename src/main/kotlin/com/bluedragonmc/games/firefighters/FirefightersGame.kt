package com.bluedragonmc.games.firefighters

import com.bluedragonmc.games.firefighters.item.SprayItem
import com.bluedragonmc.games.firefighters.module.*
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.instance.InstanceTimeModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.module.vanilla.ItemDropModule
import com.bluedragonmc.server.module.vanilla.ItemPickupModule
import com.bluedragonmc.server.module.vanilla.NaturalRegenerationModule
import com.bluedragonmc.server.utils.*
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.entity.ai.EntityAIGroupBuilder
import net.minestom.server.entity.ai.goal.FollowTargetGoal
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.EventListener
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.palette.Palette
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.network.packet.server.play.ChunkBiomesPacket
import net.minestom.server.network.packet.server.play.ExplosionPacket
import net.minestom.server.particle.Particle
import net.minestom.server.registry.RegistryKey
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.utils.time.TimeUnit
import net.minestom.server.world.biome.Biome
import java.nio.file.Paths
import java.time.Duration
import kotlin.random.Random
import kotlin.random.nextInt

class FirefightersGame(mapName: String) : Game("Firefighters", mapName) {

    val firefightersTeam =
        TeamModule.Team(Component.translatable("firefighters.team.firefighters", TextColor.color(252, 50, 50)))
    val arsonistsTeam =
        TeamModule.Team(Component.translatable("firefighters.team.arsonists", TextColor.color(252, 121, 50)))

    var explodingUntil: Long = 0L // Used to pause fire spread during cutscenes

    fun playerMessage(player: Player, msg: Component): Component {
        val team = getModule<TeamModule>().getTeam(player)
        return (team?.scoreboardTeam?.prefix ?: Component.empty()).append(
            player.name.color(team?.name?.color()).noBold()
        ).append(Component.text(": ", NamedTextColor.DARK_GRAY).noBold())
            .append(msg.colorIfAbsent(NamedTextColor.WHITE).noBold().decorate(TextDecoration.ITALIC))
    }

    private var lastExpoMessages = mutableMapOf<TeamModule.Team, Long>()

    fun expositionChatMessage(team: TeamModule.Team, playerIndex: Int, translationKey: String) {
        if (team.players.isEmpty()) return
        var playerIndex = playerIndex
        if (playerIndex == -1) playerIndex = Random.nextInt(team.players.size)

        val nextMsgTime = (lastExpoMessages[team] ?: 0) + 2_000
        val delay = if (nextMsgTime < System.currentTimeMillis()) 0 else nextMsgTime - System.currentTimeMillis()
        lastExpoMessages[team] = System.currentTimeMillis() + delay
        MinecraftServer.getSchedulerManager().buildTask {
            team.sendMessage(
                playerMessage(
                    team.players[playerIndex % team.players.size], Component.translatable(translationKey)
                )
            )
        }.delay(Duration.ofMillis(delay)).schedule().manage(this)
    }

    sealed interface Stage {

        /** Go to the next stage, returning the next stage */
        fun advance(parent: FirefightersGame): Stage

        /** The game hasn't started yet, no stages should be enabled */
        class BeforeGameStart : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                // Starting Stage 1

                // Exposition chat messages
                for (i in 1..3) {
                    parent.expositionChatMessage(
                        parent.arsonistsTeam, 0, "firefighters.stage_1.player_chat.arsonists.$i"
                    )
                    parent.expositionChatMessage(
                        parent.firefightersTeam, 0, "firefighters.stage_1.player_chat.firefighters.$i"
                    )
                }

                parent.expositionChatMessage(
                    parent.firefightersTeam, 0, "firefighters.stage_1.player_chat.firefighters.4"
                )

                val burnedRegions = mutableListOf<BurnableRegionsModule.Region>()
                parent.handleEvent(EventListener.builder(InstanceTickEvent::class.java).handler {
                    // Advance to next stage when all flammable blocks are gone
                    val regions = parent.getModuleOrNull<BurnableRegionsModule>() ?: return@handler
                    if (regions.getFlammableBlocksRemaining() == 0) {
                        parent.currentStage = parent.currentStage.advance(parent)
                    }
                    // Play explosion animation when a region reaches 100% burned
                    for ((i, region) in regions.getRegions().withIndex()) {
                        if (region.getProportionBurned() == 1.0 && !burnedRegions.contains(region) && System.currentTimeMillis() > parent.explodingUntil) {
                            burnedRegions.add(region)
                            parent.explodeRegion("burnableRegionsStage1", i)

                            parent.expositionChatMessage(
                                parent.arsonistsTeam,
                                -1,
                                "firefighters.stage_1.player_chat.arsonists.${3 + burnedRegions.size}"
                            )
                            parent.expositionChatMessage(
                                parent.firefightersTeam,
                                -1,
                                "firefighters.stage_1.player_chat.firefighters.${4 + burnedRegions.size}"
                            )
                            if (burnedRegions.size == 4) { // Last one has an extra chat message from the arsonists
                                parent.expositionChatMessage(
                                    parent.arsonistsTeam, -1, "firefighters.stage_1.player_chat.arsonists.8"
                                )
                            }
                        }
                    }
                }.expireWhen { parent.currentStage !is Stage1 }.build())
                return Stage1()
            }
        }

        /** Arsonists trying to burn down the factories */
        class Stage1 : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                parent.getModule<BurnableRegionsModule>().loadFrom(parent, "burnableRegionsStage2")
                // Stage 1 -> Stage 2
                val burnedRegions = mutableListOf<BurnableRegionsModule.Region>()
                parent.handleEvent(EventListener.builder(InstanceTickEvent::class.java).handler {
                    val regions = parent.getModuleOrNull<BurnableRegionsModule>() ?: return@handler
                    // Advance to next stage when all flammable blocks are gone
                    if (regions.getFlammableBlocksRemaining() == 0) {
                        parent.currentStage = parent.currentStage.advance(parent)
                    }
                    // Play explosion animation when a region reaches 100% burned
                    for ((i, region) in regions.getRegions().withIndex()) {
                        if (region.getProportionBurned() == 1.0 && !burnedRegions.contains(region) && System.currentTimeMillis() > parent.explodingUntil) {
                            burnedRegions.add(region)
                            parent.explodeRegion("burnableRegionsStage2", i)
                        }
                    }
                }.expireWhen { parent.currentStage !is Stage2 }.build())

                parent.firefightersTeam.sendMessage(
                    (Component.translatable(
                        "firefighters.stage_2.start.firefighters", NamedTextColor.RED, TextDecoration.BOLD
                    ) + Component.newline() + Component.translatable(
                        "firefighters.stage_2.start.firefighters.description", NamedTextColor.GRAY
                    ).decoration(TextDecoration.BOLD, false)).surroundWithSeparators()
                )
                parent.arsonistsTeam.sendMessage(
                    (Component.translatable(
                        "firefighters.stage_2.start.arsonists", NamedTextColor.GREEN, TextDecoration.BOLD
                    ) + Component.newline() + Component.translatable(
                        "firefighters.stage_2.start.arsonists.description", NamedTextColor.GRAY
                    ).decoration(TextDecoration.BOLD, false)).surroundWithSeparators()
                )

                return Stage2()
            }
        }

        /** Arsonists trying to spread the fire to the nuclear plant */
        class Stage2 : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                parent.sendMessage(Component.translatable("firefighters.stage_3.start"))
                // Stage 2 -> Stage 3
                parent.setBiome(RegistryKey.unsafeOf("bluedragonmc:zombie"))

                var spawns = 0
                var task: Task? = null
                task = MinecraftServer.getSchedulerManager().buildTask {
                    val pos = BlockVec(Random.nextInt(-100 .. 100), -52, Random.nextInt(-100 .. 100))
                    val e = EntityCreature(EntityType.ZOMBIE)
                    e.setInstance(parent.getInstance(), pos)
                    e.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.15

                    e.aiGroups.add(
                        EntityAIGroupBuilder()
                            .addTargetSelector(ClosestEntityTarget(e, 25.0) { it is Player })
                            .addGoalSelector(FollowTargetGoal(e, Duration.ofSeconds(1)))
                            .addGoalSelector(MeleeAttackGoal(e, 3.0, Duration.ofSeconds(1)))
                            .build()
                    )

                    if (spawns++ > 100) {
                        task!!.cancel()
                    }
                }.repeat(Duration.of(1, TimeUnit.SERVER_TICK)).schedule()

                return Stage3()
            }
        }

        /** Firefighters trying to escape */
        class Stage3 : Stage {
            override fun advance(parent: FirefightersGame): Stage {
                // Ending the game

                for (entity in parent.getInstance().entities) {
                    if (entity is EntityCreature) {
                        entity.aiGroups.clear()
                    }
                }

                return BeforeGameStart()
            }
        }
    }

    var currentStage: Stage = Stage.BeforeGameStart()

    override fun isInactive(): Boolean = false

    override fun initialize() {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(ConfigModule())
        use(FallDamageModule())
        use(InstanceContainerModule())
        use(InstanceTimeModule())
        use(ItemDropModule())
        use(ItemPickupModule())
        use(MOTDModule(motd = Component.translatable("firefighters.motd")))
        use(NaturalRegenerationModule())
        use(OldCombatModule())
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SidebarModule(name))
        use(SpawnpointModule(SpawnpointModule.ConfigSpawnpointProvider()))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TimedRespawnModule(seconds = 5))
        use(VoidDeathModule(threshold = -60.0))
//        use(VoteStartModule(minPlayers = 1, countdownSeconds = 5))
        use(WinModule())

        use(TeamModule(allowFriendlyFire = false)) { teamModule ->
            teamModule.teams.add(firefightersTeam)
            teamModule.teams.add(arsonistsTeam)
        }

        handleEvent<EntityAttackEvent> { event ->
            if (event.entity is EntityCreature && event.target is Player && event.entity.entityType == EntityType.ZOMBIE) {
                (event.target as Player).health -= 2
                val delta = event.target.position.sub(event.entity.position).toVec().normalize()
                OldCombatModule.takeKnockback(delta.x, delta.y, event.target, 0.5)
            }
        }

        onGameStart {
            // Assign players to teams
            for ((i, player) in players.shuffled().withIndex()) {
                if (i % 2 == 0) {
                    arsonistsTeam.addPlayer(player)
                } else {
                    firefightersTeam.addPlayer(player)
                }
            }

            firefightersTeam.sendMessage(
                (Component.translatable(
                    "firefighters.team_assignment.firefighters", firefightersTeam.name.color(), TextDecoration.BOLD
                ) + Component.newline() + Component.translatable(
                    "firefighters.team_assignment.firefighters.description", NamedTextColor.GRAY
                ).decoration(TextDecoration.BOLD, false)).surroundWithSeparators()
            )
            arsonistsTeam.sendMessage(
                (Component.translatable(
                    "firefighters.team_assignment.arsonists", arsonistsTeam.name.color(), TextDecoration.BOLD
                ) + Component.newline() + Component.translatable(
                    "firefighters.team_assignment.arsonists.description", NamedTextColor.GRAY
                ).decoration(TextDecoration.BOLD, false)).surroundWithSeparators()
            )
        }

        use(CustomItemsModule()) {
            it.addCustomItem(FLAMETHROWER)
            it.addCustomItem(EXTINGUISHER)
        }

        handleEvent<PlayerJoinGameEvent> { event ->
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                event.player.inventory.addItemStack(FLAMETHROWER.item)
                event.player.inventory.addItemStack(EXTINGUISHER.item)
                event.player.inventory.addItemStack(ItemStack.of(Material.FLINT_AND_STEEL))
            }
        }

        handleEvent<PlayerBlockInteractEvent> { event ->
            if (event.isBlockingItemUse) return@handleEvent
            if ((event.player.getItemInHand(event.hand).material() == Material.FLINT_AND_STEEL) or (event.player.getItemInHand(event.hand).material() == Material.TORCH)) {
                val direction = event.blockFace.toDirection()
                val blockPos = event.blockPosition.add(direction)
                if (event.instance.getBlock(
                        blockPos, Block.Getter.Condition.TYPE
                    ).isAir && FireSpreadModule.hasFullAdjacentFace(event.instance, blockPos)
                ) {
                    event.instance.setBlock(blockPos, Block.FIRE)
                }
            }
        }

        use(CutsceneModule())

        val powerups = listOf(
            FLAMETHROWER.item.asPowerup(arsonistsTeam, NamedTextColor.RED),
            ItemStack.of(Material.FLINT_AND_STEEL).asPowerup(arsonistsTeam, NamedTextColor.RED),
            ItemStack.of(Material.TORCH).with(DataComponents.CUSTOM_NAME, Component.text("Matches", NamedTextColor.RED)).asPowerup(arsonistsTeam, NamedTextColor.RED),
            EXTINGUISHER.item.asPowerup(firefightersTeam, NamedTextColor.AQUA)
        )
        val powerupSpawnPositions = getModule<ConfigModule>().getConfig().node("powerup", "locations").getList(Pos::class.java) ?: listOf()

        use(PowerupModule(powerups, powerupSpawnPositions, spawnAllOnStart = true))

        use(FireSpreadModule(), { System.currentTimeMillis() > explodingUntil })

        val binding = getModule<SidebarModule>().bind { player ->
            if (state != GameState.INGAME) return@bind getStatusSection()
            val list = getStatusSection().toMutableList()

            when (currentStage) {
                is Stage.Stage1 -> {
                    val regions = getModule<BurnableRegionsModule>()
                    val phase = (getInstance().worldAge % 20L) / 20.0
                    val colors = "#a10000:#ea2300:#ff8100:#f25500:#d80000"
                    val title = miniMessage.deserialize("<bold><gradient:$colors:${-phase}>BURN THE FACTORIES")
                    list += getSpacer()
                    list += title
                    list += regions.getScoreboardText()
                    list += getSpacer()
                }

                is Stage.Stage2 -> {
                    val regions = getModule<BurnableRegionsModule>()
                    val phase = (getInstance().worldAge % 20L) / 20.0
                    val colors = "#acfc8d:#4af407:#18ce64:#057a01"
                    val title = miniMessage.deserialize("<bold><gradient:$colors:${-phase}>NUCLEAR PLANT")
                    list += getSpacer()
                    list += title
                    list += regions.getScoreboardText()
                    list += getSpacer()
                }

                else -> {}
            }
            return@bind list
        }

        use(
            BurnableRegionsModule("burnableRegionsStage1"),
            { currentStage is Stage.Stage1 || currentStage is Stage.Stage2 })

        handleEvent<InstanceTickEvent> { event ->
            if (event.instance.worldAge % 2L == 0L) {
                // Update scoreboard ~10x per second for the "Burn status" title animation
                binding.update()
            }
        }

        onGameStart {
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                if (currentStage is Stage.BeforeGameStart) { // sanity check, should always be true
                    currentStage = currentStage.advance(this)
                } else {
                    currentStage = Stage.Stage1()
                }
            }
        }

        handleEvent<PlayerJoinGameEvent> {
            EventDispatcher.call(GameStartEvent(this))
            state = GameState.INGAME
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                it.player.gameMode = GameMode.CREATIVE
            }
        }
    }

    fun explodeRegion(configKey: String, index: Int) {
        val stepsPerCurve = 7
        val msPerPoint = 300L
        val numPointsBeforeExplosion = 3
        val instance = getInstance()

        val region = getModuleOrNull<ConfigModule>()?.getConfig()?.node(configKey)?.childrenList()?.get(index) ?: return
        val points = region.node("cutscene")?.getList(Pos::class.java) ?: return
        val pos1 = region.node("start").get(Pos::class.java) ?: return
        val pos2 = region.node("end").get(Pos::class.java) ?: return

        explodingUntil = System.currentTimeMillis() + msPerPoint * stepsPerCurve * points.size

        for (player in players) {
            getModule<CutsceneModule>().playCutscene(
                player, this, points, msPerPoint = msPerPoint, stepsPerCurve = stepsPerCurve
            )
        }
        MinecraftServer.getSchedulerManager().buildTask {
            // For each block, have a chance of turning into an entity and exploding and a chance of just disappearing
            val centerPos = Vec((pos1.x + pos2.x) / 2, pos1.y.coerceAtMost(pos2.y) - 5, (pos1.z + pos2.z) / 2)

            for (pos in (BlockVec(pos1)..BlockVec(pos2))) {
                val block = instance.getBlock(pos)
                if (block.isAir || block.compare(Block.FIRE)) continue
                val rand = Random.nextDouble()
                if (rand < 0.5) {
                    instance.setBlock(pos, Block.AIR)
                }
                if (rand < 0.3) {
                    // Turn block into an entity and send away from center
                    Entity(EntityType.BLOCK_DISPLAY).apply {
                        val meta = entityMeta as BlockDisplayMeta
                        meta.setBlockState(block)
                        setInstance(instance, pos)
                        velocity = pos.sub(centerPos).toVec().mul(5 * rand)
                        MinecraftServer.getSchedulerManager().buildTask {
                            instance.setBlock(position, block)
                            remove()
                        }.delay(Duration.ofMillis(msPerPoint * 20)).schedule()
                    }
                }
                if (rand < 0.1) {
                    // Play an explosion particle effect
                    MinecraftServer.getSchedulerManager().buildTask {
                        instance.sendGroupedPacket(
                            ExplosionPacket(
                                pos,
                                Pos.ZERO,
                                Particle.fromKey(Key.key("minecraft:explosion"))!!,
                                SoundEvent.ENTITY_GENERIC_EXPLODE
                            )
                        )
                        FireSpreadModule.iterateAdjacentBlocks(pos).forEach { it ->
                            if (Random.nextFloat() < 0.3) {
                                FireSpreadModule.setFire(instance, it)
                            }
                        }
                    }.delay(Duration.of((rand * 300).toLong(), TimeUnit.SERVER_TICK)).schedule()
                }
            }
        }.delay(Duration.ofMillis(msPerPoint * stepsPerCurve * numPointsBeforeExplosion)).schedule().manage(this)
    }

    private fun setBiome(biome: RegistryKey<Biome>) {
        val biomeId = MinecraftServer.getBiomeRegistry().getId(biome)
        val instance = getInstance()
        val packetData = mutableListOf<ChunkBiomesPacket.ChunkBiomeData>()
        for (x in -12..5) {
            for (z in -12..5) {
                val chunk = getInstance().getChunk(x, z) ?: continue
                val chunkBiomes = ByteArrayList.of()
                chunk.sections.forEachIndexed { y, section ->
                    section.biomePalette().fill(biomeId)
                    instance.invalidateSection(x, y + chunk.minSection, z)
                    val sectionBiomes = NetworkBuffer.makeArray { buffer ->
                        Palette.biomeSerializer(section.biomePalette().count()).write(buffer, section.biomePalette())
                    }
                    chunkBiomes.addElements(chunkBiomes.size, sectionBiomes)
                }
                val arr = ByteArray(chunkBiomes.size)
                chunkBiomes.toArray(arr)
                packetData.add(ChunkBiomesPacket.ChunkBiomeData(x, z, arr))
            }
        }

        sendGroupedPacket(ChunkBiomesPacket(packetData))
    }

    private companion object {
        val FLAMETHROWER = SprayItem(
            "flamethrower",
            ItemStack.builder(Material.BLAZE_ROD)
                .customName(Component.translatable("item.flamethrower", NamedTextColor.DARK_RED).noItalic())
                .set(DataComponents.MAX_DAMAGE, 64)
                .set(DataComponents.MAX_STACK_SIZE, 1).build(),
            SprayItem.SprayItemType.FIRE_SPREAD
        )
        val EXTINGUISHER = SprayItem(
            "extinguisher",
            ItemStack.builder(Material.GLOW_INK_SAC)
                .customName(Component.translatable("item.extinguisher", NamedTextColor.DARK_AQUA).noItalic())
                .set(DataComponents.MAX_DAMAGE, 64)
                .set(DataComponents.MAX_STACK_SIZE, 1).build(),
            SprayItem.SprayItemType.FIRE_EXTINGUISH
        )

        fun ItemStack.asPowerup(team: TeamModule.Team? = null, glowColor: NamedTextColor) = PowerupModule.Powerup(
            name = get(DataComponents.CUSTOM_NAME) ?: Component.translatable(material().registry().translationKey()),
            icon = material(),
            visibilityRule = { player -> team?.players?.contains(player) ?: true },
            { player ->
                player.inventory.addItemStack(this)
            },
            glowColor = glowColor
        )
    }
}