package com.bluedragonmc.games.firefighters.module

import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block

class OnFireModule(player: Player) {
    fun initialize (player: Player): Boolean {
        val instance = player.getInstance();
        val playerPosition = player.getPosition();
        val targetBlock = instance.getBlock(playerPosition);
        return Block.FIRE.compare(targetBlock)
    }
}