package com.bluedragonmc.games.firefighters

import com.bluedragonmc.example.server.GAME_NAME
import com.bluedragonmc.games.firefighters.item.FireStartingItem
import com.bluedragonmc.games.firefighters.item.SprayItem
import com.bluedragonmc.games.firefighters.item.WaterBucketItem
import com.bluedragonmc.games.firefighters.module.*
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.instance.InstanceTimeModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.*
import com.bluedragonmc.server.utils.*
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
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
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.event.EventListener
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.player.PlayerUseItemEvent
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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextInt

class FirefightersGame(mapName: String) : Game(GAME_NAME, mapName) {

    val firefightersTeam =
        TeamModule.Team(Component.translatable("firefighters.team.firefighters", TextColor.color(252, 50, 50)))
    val arsonistsTeam =
        TeamModule.Team(Component.translatable("firefighters.team.arsonists", TextColor.color(252, 121, 50)))

    var explodingUntil: Long = 0L // Used to pause fire spread during cutscenes
    var timeLeft = -1 // Time left before police arrive (firefighters win), in ticks

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
                parent.timeLeft = ServerFlag.SERVER_TICKS_PER_SECOND * 5 * 60

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
                        if (region.getProportionBurned() >= 0.9 && !burnedRegions.contains(region) && System.currentTimeMillis() > parent.explodingUntil) {
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

                // Open the iron door to the reactor (lol)
                parent.getInstance()
                    .setBlock(-11, -52, -30, parent.getInstance().getBlock(-11, -52, -30).withProperty("open", "true"))
                parent.getInstance()
                    .setBlock(-11, -51, -30, parent.getInstance().getBlock(-11, -51, -30).withProperty("open", "true"))

                // Observe burned blocks
                val almostBurnedRegions =
                    mutableListOf<BurnableRegionsModule.Region>() // used for the "almost there" chat message
                val burnedRegions = mutableListOf<BurnableRegionsModule.Region>()
                parent.handleEvent(EventListener.builder(InstanceTickEvent::class.java).handler {
                    val regions = parent.getModuleOrNull<BurnableRegionsModule>() ?: return@handler
                    // Advance to next stage when all flammable blocks are gone
                    if (regions.getFlammableBlocksRemaining() == 0) {
                        parent.currentStage = parent.currentStage.advance(parent)
                    }
                    // Play explosion animation when a region reaches 100% burned
                    for ((i, region) in regions.getRegions().withIndex()) {
                        if (region.getProportionBurned() >= 0.7 && !almostBurnedRegions.contains(region)) {
                            parent.expositionChatMessage(
                                parent.arsonistsTeam, -1, "firefighters.stage_2.player_chat.arsonists.1"
                            )
                            parent.expositionChatMessage(
                                parent.firefightersTeam, -1, "firefighters.stage_2.player_chat.firefighters.1"
                            )
                            almostBurnedRegions.add(region)
                        }
                        if (region.getProportionBurned() >= 0.9 && !burnedRegions.contains(region) && System.currentTimeMillis() > parent.explodingUntil) {
                            burnedRegions.add(region)
                            parent.explodeRegion("burnableRegionsStage2", i)
                            parent.expositionChatMessage(
                                parent.arsonistsTeam, -1, "firefighters.stage_2.player_chat.arsonists.2"
                            )
                            parent.expositionChatMessage(
                                parent.firefightersTeam, -1, "firefighters.stage_2.player_chat.firefighters.2"
                            )
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
                // Stage 2 -> Stage 3
                parent.sendMessage(
                    (Component.translatable(
                        "firefighters.stage_3.start", NamedTextColor.RED, TextDecoration.BOLD
                    ) + Component.newline() + Component.translatable(
                        "firefighters.stage_3.start.description", NamedTextColor.GRAY
                    ).decoration(TextDecoration.BOLD, false)).surroundWithSeparators()
                )
                parent.setBiome(RegistryKey.unsafeOf("bluedragonmc:zombie"))

                // Exposition chat messages
                for (i in 1..3) {
                    parent.expositionChatMessage(
                        parent.arsonistsTeam, -1, "firefighters.stage_3.player_chat.arsonists.$i"
                    )
                    parent.expositionChatMessage(
                        parent.firefightersTeam, -1, "firefighters.stage_3.player_chat.firefighters.$i"
                    )
                }

                var spawns = 0
                var task: Task? = null
                task = MinecraftServer.getSchedulerManager().buildTask {
                    val pos = BlockVec(Random.nextInt(-100..100), -52, Random.nextInt(-100..100))
                    val e = EntityCreature(EntityType.ZOMBIE)
                    e.setInstance(parent.getInstance(), pos)
                    e.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.15

                    e.aiGroups.add(
                        EntityAIGroupBuilder()
                            .addTargetSelector(
                                ClosestEntityTarget(
                                    e,
                                    25.0
                                ) {
                                    it is Player && parent.getModule<TeamModule>().getTeam(it) == parent.arsonistsTeam
                                })
                            .addGoalSelector(FollowTargetGoal(e, Duration.ofSeconds(1)))
                            .addGoalSelector(MeleeAttackGoal(e, 3.0, Duration.ofSeconds(1)))
                            .build()
                    )

                    if (spawns++ > 100) {
                        task!!.cancel()
                    }
                }.repeat(Duration.of(1, TimeUnit.SERVER_TICK)).schedule()
                parent.handleEvent(EventListener.builder(InstanceTickEvent::class.java).handler { event ->
                    val aliveArsonists = parent.arsonistsTeam.players.filter { player ->
                        !parent.getModule<SpectatorModule>().isSpectating(player)
                    }
                    if (aliveArsonists.isEmpty()) {
                        // The zombies or firefighters have killed all the arsonists. They win!
                        parent.currentStage = parent.currentStage.advance(parent)
                        parent.getModule<WinModule>().declareWinner(parent.firefightersTeam)
                    } else if (aliveArsonists.all { parent.hasEscaped(it) }) {
                        // All arsonists have escaped. They win!
                        parent.currentStage = parent.currentStage.advance(parent)
                        parent.getModule<WinModule>().declareWinner(parent.arsonistsTeam)
                    }
                }.expireWhen { parent.currentStage !is Stage3 }.build())

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
        use(DoorsModule())
        use(IronDoorPressurePlateModule())
        use(FallDamageModule())
        use(InstanceContainerModule())
        use(InstanceTimeModule())
        use(ItemDropModule())
        use(ItemPickupModule())
        use(MOTDModule(motd = Component.translatable("firefighters.motd")))
        use(NaturalRegenerationModule())
        use(OldCombatModule())
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(name))
        use(SpawnpointModule(SpawnpointModule.ConfigSpawnpointProvider()))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TimedRespawnModule(seconds = 5), { event ->
            if (event is PlayerDeathEvent) {
                // During stage 3, only firefighters can respawn
                if (currentStage is Stage.Stage3 && getModule<TeamModule>().getTeam(event.player) == arsonistsTeam) {
                    getModule<SpectatorModule>().addSpectator(event.player)
                    return@use false
                }
                return@use true
            }
            return@use true
        })
        use(VoidDeathModule(threshold = -60.0))
        use(VoteStartModule(minPlayers = 1))
//        use(VoteStartModule(minPlayers = 1, countdownSeconds = 5))
        use(WinModule())
        use(CustomDeathMessageModule())

        handleEvent<PlayerUseItemEvent> { event ->
            // TODO - why does this make it work?? why doesn't the module receive the event on its own???????
            getModule<CustomItemsModule>().eventNode.call(event) // wtf??
        }

        handleEvent<PlayerJoinGameEvent> {
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                it.player.gameMode = GameMode.ADVENTURE
            }
        }

        use(TeamModule(allowFriendlyFire = false)) { teamModule ->
            teamModule.teams.add(firefightersTeam)
            teamModule.teams.add(arsonistsTeam)
        }

        handleEvent<EntityAttackEvent> { event ->
            if (event.entity is EntityCreature && event.target is Player && event.entity.entityType == EntityType.ZOMBIE) {
                (event.target as Player).health -= 2
                val delta = event.entity.position.sub(event.target.position).toVec().normalize()
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
            it.addCustomItem(FLINT_AND_STEEL)
            it.addCustomItem(MATCHES)
            it.addCustomItem(WATER_BUCKET)
        }

        use(CutsceneModule()) { cutsceneModule ->
            handleEvent<GameStartEvent> {
                val introCutsceneNode = getModule<ConfigModule>().getConfig().node("introCutscene")
                val arsonistCutscene = introCutsceneNode.node("arsonist").getList(Pos::class.java)
                val firefighterCutscene = introCutsceneNode.node("firefighter").getList(Pos::class.java)
                if (firefighterCutscene != null)
                    firefightersTeam.players.forEach { player ->
                        cutsceneModule.playCutscene(
                            player,
                            this,
                            firefighterCutscene,
                            stepsPerCurve = 10,
                            msPerPoint = 100
                        )
                    }
                if (arsonistCutscene != null)
                    arsonistsTeam.players.forEach { player ->
                        cutsceneModule.playCutscene(
                            player,
                            this,
                            arsonistCutscene,
                            stepsPerCurve = 10,
                            msPerPoint = 100
                        )
                    }
            }
        }

        val powerups = listOf(
            FLAMETHROWER.item.asPowerup(arsonistsTeam, NamedTextColor.RED),
            FLINT_AND_STEEL.item.asPowerup(arsonistsTeam, NamedTextColor.RED),
            MATCHES.item.asPowerup(arsonistsTeam, NamedTextColor.RED),
            EXTINGUISHER.item.asPowerup(firefightersTeam, NamedTextColor.AQUA),
            WATER_BUCKET.item.asPowerup(firefightersTeam, NamedTextColor.AQUA)
        )
        val powerupSpawnPositions =
            getModule<ConfigModule>().getConfig().node("powerup", "locations").getList(Pos::class.java) ?: listOf()

        use(PowerupModule(powerups, powerupSpawnPositions, spawnRate = Duration.ofSeconds(1), spawnAllOnStart = true))

        // defualt movement speed is 0.1
        use(InventoryMovementSpeedModule(baseSpeed = 0.13, slowdownPerItem = 0.01, minimumSpeed = 0.08))

        use(FireSpreadModule(), { System.currentTimeMillis() > explodingUntil })

        val binding = getModule<SidebarModule>().bind { player ->
            if (state != GameState.INGAME) return@bind getStatusSection()
            val list = getStatusSection().toMutableList()

            val minutes = timeLeft / ServerFlag.SERVER_TICKS_PER_SECOND / 60
            val seconds = timeLeft / ServerFlag.SERVER_TICKS_PER_SECOND % 60
            val timerText = Component.text("Police arrive in: ", NamedTextColor.GRAY)
                .append(Component.text("$minutes:${seconds.toString().padStart(2, '0')}", NamedTextColor.RED))

            val isArsonist = getModule<TeamModule>().getTeam(player) == arsonistsTeam

            when (currentStage) {
                is Stage.Stage1 -> {
                    val regions = getModule<BurnableRegionsModule>()
                    val phase = (getInstance().worldAge % 20L) / 20.0
                    val colors = "#a10000:#ea2300:#ff8100:#f25500:#d80000"
                    val title =
                        miniMessage.deserialize("<bold><gradient:$colors:${-phase}>${if (isArsonist) "BURN THE FACTORIES" else "PROTECT THE FACTORIES"}")
                    list += getSpacer()
                    list += title
                    list += timerText
                    list += getSpacer()
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
                    list += timerText
                    list += getSpacer()
                    list += regions.getScoreboardText()
                    list += getSpacer()
                }

                is Stage.Stage3 -> {
                    val aliveArsonists = arsonistsTeam.players
                        .filter { !getModule<SpectatorModule>().isSpectating(it) }
                    val escapedArsonists = aliveArsonists.count { hasEscaped(it) }
                    val phase = (getInstance().worldAge % 20L) / 20.0
                    val colors = "#e5acf9:#bb24f2:#f224da"
                    list += miniMessage.deserialize("<bold><gradient:$colors:${-phase}>${if (isArsonist) "ESCAPE THE PLANT" else "CAPTURE THE ARSONISTS"}")
                    if (isArsonist) {
                        list += Component.text("Go to the village", NamedTextColor.GRAY)
                        list += Component.text("to the west!", NamedTextColor.GRAY)
                    } else {
                        list += Component.text("Don't let them get to", NamedTextColor.GRAY)
                        list += Component.text("the village to the west!", NamedTextColor.GRAY)
                    }
                    list += getSpacer()
                    list += Component.text("Arsonists escaped: ", NamedTextColor.GRAY)
                        .append(Component.text(escapedArsonists, NamedTextColor.RED))
                        .append(Component.text("/${aliveArsonists}", NamedTextColor.GRAY))
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
            if (currentStage is Stage.Stage1 || currentStage is Stage.Stage2) {
                if (timeLeft > 0) {
                    timeLeft--
                } else if (timeLeft == 0) {
                    timeLeft = -1
                    getModule<WinModule>().declareWinner(firefightersTeam)
                }
            }
        }

        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (playerOnFire(player)) {
                player.fireTicks = ServerFlag.SERVER_TICKS_PER_SECOND * 3
            }

            if (player.isOnFire) {
                if ((player.aliveTicks % 20).toInt() == 0) {
                    player.damage(DamageType.ON_FIRE, 2.0F)
                }
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
    }

    /**
     * Returns whether an arsonist has "escaped" the nuclear fallout.
     * TODO: make configurable
     */
    fun hasEscaped(it: Player) = (it.position.x > -120 && it.position.blockZ() in (0..105)) ||
            (it.position.x > -140 && it.position.blockZ() in (-40..0))

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
            // Actually do the explosion

            // First, kill every player inside the factory
            val minimum = BlockVec(
                min(pos1.blockX(), pos2.blockX()),
                min(pos1.blockY(), pos2.blockY()),
                min(pos1.blockZ(), pos2.blockZ())
            )
            val maximum = BlockVec(
                max(pos1.blockX(), pos2.blockX()),
                max(pos1.blockY(), pos2.blockY()),
                max(pos1.blockZ(), pos2.blockZ())
            )
            for (player in players) {
                if (player.position.x in (minimum.x..maximum.x) && player.position.y in (minimum.y..maximum.y) && player.position.z in (minimum.z..maximum.z)) {
                    player.damage(Damage(DamageType.EXPLOSION, null, null, null, Float.MAX_VALUE))
                }
            }

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

    private fun playerOnFire(player: Player): Boolean {
        val instance = player.instance
        val playerPosition = player.position
        val targetBlock = instance.getBlock(playerPosition)
        return Block.FIRE.compare(targetBlock)
    }


    private companion object {
        val FLINT_AND_STEEL = FireStartingItem(
            "flint_and_steel", ItemStack.builder(Material.FLINT_AND_STEEL)
                .set(DataComponents.MAX_DAMAGE, 64)
                .build()
        )
        val MATCHES = FireStartingItem(
            "matches",
            ItemStack.builder(Material.TORCH)
                .set(DataComponents.CUSTOM_NAME, Component.text("Matches", NamedTextColor.RED))
                .set(DataComponents.MAX_DAMAGE, 30)
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .build()
        )
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

        val WATER_BUCKET = WaterBucketItem(
            "water_bucket",
            ItemStack.builder(Material.WATER_BUCKET)
                .customName(Component.translatable("item.water_bucket", NamedTextColor.DARK_AQUA).noItalic())
                .set(DataComponents.MAX_DAMAGE, 30)
                .build()
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