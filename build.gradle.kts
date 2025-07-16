plugins {
    kotlin("jvm") version "2.1.10"
}

group = "com.bluedragonmc.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.BlueDragonMC.Server:common:763c96917d") {
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