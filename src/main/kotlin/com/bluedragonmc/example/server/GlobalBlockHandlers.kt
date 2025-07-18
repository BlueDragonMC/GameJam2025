package com.bluedragonmc.example.server

import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.tag.Tag

object GlobalBlockHandlers {
    fun hook() {
        registerHandler(
            "minecraft:sign", listOf(
                // https://minecraft.wiki/w/Sign#Block_data
                Tag.Byte("is_waxed"),
                Tag.NBT("front_text"),
                Tag.NBT("back_text"),
            )
        )
        registerHandler(
            "minecraft:hanging_sign", listOf(
                // https://minecraft.wiki/w/Hanging_Sign#Block_data
                Tag.Byte("is_waxed"),
                Tag.NBT("front_text"),
                Tag.NBT("back_text"),
            )
        )
    }

    private fun registerHandler(registryName: String, blockEntityTags: List<Tag<*>>) =
        MinecraftServer.getBlockManager().registerHandler(registryName) {
            createHandler(registryName, blockEntityTags)
        }

    private fun createHandler(registryName: String, blockEntityTags: List<Tag<*>>) = object : BlockHandler {
        override fun getKey() = Key.key(registryName)
        override fun getBlockEntityTags() = blockEntityTags
    }
}