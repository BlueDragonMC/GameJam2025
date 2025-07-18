plugins {
    kotlin("jvm") version "2.1.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.bluedragonmc.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://reposilite.atlasengine.ca/public")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.BlueDragonMC.Server:common:5693662162") {
        exclude(group = "org.tinylog")
    }
    implementation("net.kyori:adventure-text-minimessage:4.11.0")
    implementation("net.minestom:minestom:2025.07.11-1.21.7")
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    implementation("ch.qos.logback:logback-classic:1.5.13")

    compileOnly("com.github.bluedragonmc:rpc:fb16ef4cc5")
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks["shadowJar"])
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.bluedragonmc.example.server.MainKt"
    }
}
