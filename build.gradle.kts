plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.cyljacky02"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    // Paper API (provides Adventure, SLF4J, Netty)
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // === Libraries loaded by Paper's Library Loader (declared in paper-plugin.yml) ===
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.7")
    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly("org.spongepowered:configurate-yaml:4.2.0")
    compileOnly("org.spongepowered:configurate-extra-kotlin:4.2.0")
    
    // Reactor - required by Lettuce (loaded via library loader, not shaded)
    compileOnly("io.projectreactor:reactor-core:3.8.1")
    
    // Netty - provided by Paper at runtime, needed for compile-time access to DefaultAddressResolverGroup
    compileOnly("io.netty:netty-resolver:4.1.118.Final")

    // === Shaded Redis client ===
    // Lettuce is shaded and relocated, but Netty is NOT relocated
    // because InvUI needs Paper's native io.netty for packet interception.
    // We exclude Netty from Lettuce and use Paper's native Netty.
    // We also exclude Reactor (loaded via library loader to avoid duplication).
    implementation("io.lettuce:lettuce-core:7.2.1.RELEASE") {
        exclude(group = "io.netty")
        exclude(group = "io.projectreactor")
        exclude(group = "org.reactivestreams")
    }

    // === Shaded dependencies (not on Maven Central or need relocation) ===
    // GUI - InvUI v2 (shaded but NOT relocated)
    // - Cannot use Paper library loader: InvUI v2 uses paperweight-userdev with NMS code
    // - Cannot relocate: Uses io.netty.channel.ChannelDuplexHandler for packet interception
    // - Shared library pattern: All Loafy plugins use LoafyLib's InvUI instance
    implementation("xyz.xenondevs.invui:invui:2.0.0-alpha.25")
    implementation("xyz.xenondevs.invui:invui-kotlin:2.0.0-alpha.25")
    
    // Commands - Lamp (NOT shaded - each dependent plugin should shade their own copy)
    // Lamp's BukkitLamp.builder(plugin) binds commands to a specific plugin instance,
    // making shared library usage problematic. Each plugin needs its own relocated copy.
    // Kept as compileOnly for API reference only.
    compileOnly("io.github.revxrsal:lamp.common:4.0.0-rc.14")
    compileOnly("io.github.revxrsal:lamp.bukkit:4.0.0-rc.14")

    // Adventure/MiniMessage (provided by Paper)
    compileOnly("net.kyori:adventure-api:4.25.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.25.0")

    // PacketEvents - optional soft dependency for glowing entity support
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.1")

    // Testing - Kotest
    testImplementation("io.kotest:kotest-runner-junit5-jvm:6.0.7")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.7")
    testImplementation("io.kotest:kotest-property-jvm:6.0.7")
    testImplementation("io.mockk:mockk:1.14.2")

    // Adventure API for tests (needed since Paper API is compileOnly)
    testImplementation("net.kyori:adventure-api:4.25.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.25.0")

    // Paper API for tests (needed for Plugin interface, etc.)
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Library loader deps for tests (since they're compileOnly for main)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.zaxxer:HikariCP:7.0.2")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.7")
    testImplementation("org.spongepowered:configurate-hocon:4.2.0")
    testImplementation("org.spongepowered:configurate-yaml:4.2.0")
    testImplementation("org.spongepowered:configurate-extra-kotlin:4.2.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    
    // Minimize JAR - only include classes actually used
    minimize {
        // Don't minimize Lettuce - it uses reflection and service loading
        exclude(dependency("io.lettuce:lettuce-core:.*"))
        // Don't minimize InvUI - it uses reflection for NMS access
        exclude(dependency("xyz.xenondevs.invui:.*"))
    }
    
    // Relocations
    // Lettuce relocated (Netty excluded - uses Paper's native Netty)
    relocate("io.lettuce", "me.cyljacky02.loafylib.libs.lettuce")
    relocate("redis.clients", "me.cyljacky02.loafylib.libs.redis.clients")
    
    // NOTE: InvUI is NOT relocated - it uses io.netty.channel.ChannelDuplexHandler
    // for packet interception. InvUI v2 injects into Paper's Netty pipeline:
    //   channel.pipeline().addBefore(MC_PACKET_HANDLER_NAME, invuiPacketHandlerName, packetHandler)
    // Relocating would update io.netty references, breaking the pipeline injection.
    // relocate("xyz.xenondevs.invui", "me.cyljacky02.loafylib.libs.invui")
    // relocate("xyz.xenondevs.inventoryaccess", "me.cyljacky02.loafylib.libs.inventoryaccess")
    
    // NOTE: Lamp is NOT shaded in LoafyLib - each dependent plugin should shade their own copy
    // because BukkitLamp.builder(plugin) binds commands to a specific plugin instance.
    
    // Exclude Paper-provided classes
    exclude("net/kyori/**")
    exclude("org/slf4j/**")
    exclude("kotlin/**")
    exclude("org/jetbrains/**")
    exclude("org/jspecify/**")
    exclude("org/intellij/**")
    
    // Exclude unnecessary files
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/NOTICE*")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/DEPENDENCIES")
    exclude("**/module-info.class")
    exclude("META-INF/native-image/**")
    exclude("META-INF/io.netty.versions.properties")
    
    // Exclude InvUI colors.bin (large file, not needed for basic usage)
    exclude("colors.bin")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("1.21.11")
}
