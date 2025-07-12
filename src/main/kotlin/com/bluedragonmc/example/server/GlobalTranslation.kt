package com.bluedragonmc.example.server

import com.bluedragonmc.server.DEFAULT_LOCALE
import com.bluedragonmc.server.NAMESPACE
import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry
import net.minestom.server.adventure.MinestomAdventure
import org.slf4j.LoggerFactory
import java.util.*

object GlobalTranslation {
    private val logger = LoggerFactory.getLogger(GlobalTranslation::class.java)

    fun hook() {
        // https://github.com/BlueDragonMC/Server/blob/main/src/main/kotlin/com/bluedragonmc/server/bootstrap/GlobalTranslation.kt
        MinestomAdventure.AUTOMATIC_COMPONENT_TRANSLATION = true

        val classLoader = GlobalTranslation::class.java.classLoader
        val registry = TranslationRegistry.create(Key.key(NAMESPACE, "i18n"))
        val translations = Properties().apply {
            load(classLoader.getResourceAsStream("i18n.properties"))
        }
        val languages = translations.keys.map { it.toString() }.filter { it.startsWith("lang_") }
        for (language in languages) {
            val path = translations.getProperty(language)
            val locale = Locale.forLanguageTag(language.substringAfter("lang_"))
            val bundle = PropertyResourceBundle(classLoader.getResourceAsStream(path))
            registry.registerAll(locale, bundle, true)
            logger.debug(
                "Registered language {} (locale: {}) from file {}",
                language,
                locale,
                translations.getProperty(language)
            )
        }
        registry.defaultLocale(DEFAULT_LOCALE)

        GlobalTranslator.translator().addSource(registry)
    }
}