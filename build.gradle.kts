plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    `maven-publish`
}

group = "me.cyljacky02"
// version comes from gradle.properties so release-please can bump it.

// =============================================================================
// Platform version derivation
//
// The catalog holds the FULL paper-api artifact version. Upstream uses two
// different schemes, so we parse rather than construct:
//   "1.21.11-R0.1-SNAPSHOT"  -> minecraft 1.21.11, api-version 1.21
//   "26.2.build.111-stable"  -> minecraft 26.2,    api-version 26.2
// Everything that needs a Minecraft version reads it from here, so a bump to
// the catalog propagates to run-paper, paper-plugin.yml and CI at once.
// =============================================================================
val paperApiVersion: String = libs.versions.paper.api.get()

val minecraftVersion: String =
    Regex("""^(.+?)-R\d+\.\d+-SNAPSHOT$""").find(paperApiVersion)?.groupValues?.get(1)
        ?: Regex("""^(.+?)\.build\.\d+-[A-Za-z]+$""").find(paperApiVersion)?.groupValues?.get(1)
        ?: error(
            "Unrecognised paper-api version scheme: '$paperApiVersion'. " +
                "Expected '<mc>-R0.1-SNAPSHOT' or '<mc>.build.<n>-<channel>'.",
        )

/** The `api-version` declared in paper-plugin.yml — the Minecraft release line. */
val pluginApiVersion: String =
    minecraftVersion.substringBefore('-').split('.').take(2).joinToString(".")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://maven.enginehub.org/repo/") // WorldGuard
    maven("https://jitpack.io") // LandsAPI
}

// =============================================================================
// Paper library loader
//
// Dependencies in the `paperLibrary` configuration are downloaded at runtime by
// Paper's MavenLibraryResolver (see LoafyLibPluginLoader.java). They are NOT
// shaded into the jar.
//
// The coordinates are written into the jar by :generatePaperLibraries and read
// back by the loader, so the version we compile against and the version the
// server fetches are the same string by construction. Never hardcode these
// coordinates in Java again — a bot updating the catalog cannot edit string
// literals, and the resulting compile/runtime skew only fails on a live server.
// =============================================================================
/**
 * Renders a catalog entry as `group:name:version`.
 *
 * Used for the generated manifest, and for the two dependencies that need an
 * `exclude` block: passing a catalog `Provider` together with a configuration
 * closure silently discards the exclusions (the trailing lambda binds to an
 * overload that never applies it), which quietly re-added every Netty module to
 * the shaded jar. String notation takes the documented
 * `Action<ExternalModuleDependency>` overload, so the excludes actually apply.
 */
fun Provider<MinimalExternalModuleDependency>.coordinate(): String =
    get().let { "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}" }

val paperLibrary: Configuration by configurations.creating

/** Runtime libraries, in the order they appear in the generated manifest. */
val paperLibraries = listOf(
    // Kotlin stdlib must match the compiler version — the catalog shares one ref.
    libs.kotlin.stdlib,
    // kotlin-reflect is required by configurate-extra-kotlin to instantiate
    // Kotlin data classes using default constructor arguments.
    libs.kotlin.reflect,
    libs.kotlinx.coroutines.core,
    // Kotlinx Serialization — Redis sync payloads and general serialization.
    libs.kotlinx.serialization.core,
    libs.kotlinx.serialization.json,
    libs.hikaricp,
    libs.mariadb.client,
    // SQLite JDBC driver for local file-based databases.
    libs.sqlite.jdbc,
    libs.configurate.hocon,
    libs.configurate.yaml,
    libs.configurate.extra.kotlin,
    // Reactor Core is required by Lettuce for reactive streams and is not
    // provided by Paper. Lettuce itself is shaded, not loaded here.
    libs.reactor.core,
)

dependencies {
    // Paper API (provides Adventure, SLF4J, Netty)
    compileOnly(libs.paper.api)

    // Runtime libraries: compiled against, resolved by Paper at runtime.
    paperLibraries.forEach { paperLibrary(it) }

    // Netty — provided by Paper at runtime, needed for compile-time access to
    // DefaultAddressResolverGroup.
    compileOnly(libs.netty.resolver)

    // === Shaded Redis client ===
    // Lettuce is shaded and relocated. Most Netty modules are NOT relocated because:
    // 1. InvUI needs Paper's native io.netty for packet interception
    // 2. Paper already provides Netty at runtime
    //
    // IMPORTANT: We MUST include netty-resolver-dns because Lettuce's DefaultClientResources
    // has a static initializer that references DnsAddressResolverGroup and DefaultDnsCnameCache.
    // This static field is initialized when the class is loaded, BEFORE any builder configuration.
    // Without netty-resolver-dns, class loading fails with NoClassDefFoundError: DnsCnameCache.
    //
    // We relocate netty-resolver-dns to avoid conflicts with Paper's Netty (which doesn't include it).
    implementation(libs.lettuce.core.coordinate()) {
        exclude(group = "io.projectreactor")
        exclude(group = "org.reactivestreams")
        // Exclude all netty EXCEPT netty-resolver-dns which is required for Lettuce class loading
        exclude(group = "io.netty", module = "netty-common")
        exclude(group = "io.netty", module = "netty-handler")
        exclude(group = "io.netty", module = "netty-transport")
        exclude(group = "io.netty", module = "netty-buffer")
        exclude(group = "io.netty", module = "netty-codec")
        exclude(group = "io.netty", module = "netty-resolver")
        exclude(group = "io.netty", module = "netty-transport-native-unix-common")
    }

    // netty-resolver-dns is required for Lettuce's static initializer (DefaultClientResources)
    // We shade and relocate it to avoid conflicts with Paper's Netty
    implementation(libs.netty.resolver.dns)

    // === Shaded dependencies (not on Maven Central or need relocation) ===
    // GUI - InvUI v2 (shaded but NOT relocated)
    // - Cannot use Paper library loader: InvUI v2 uses paperweight-userdev with NMS code
    // - Cannot relocate: Uses io.netty.channel.ChannelDuplexHandler for packet interception
    // - Shared library pattern: All Loafy plugins use LoafyLib's InvUI instance
    implementation(libs.invui)
    implementation(libs.invui.kotlin)

    // Commands - Lamp (NOT shaded - each dependent plugin should shade their own copy)
    // Lamp's BukkitLamp.builder(plugin) binds commands to a specific plugin instance,
    // making shared library usage problematic. Each plugin needs its own relocated copy.
    // Kept as compileOnly for API reference only.
    compileOnly(libs.lamp.common)
    compileOnly(libs.lamp.bukkit)

    // Adventure/MiniMessage (provided by Paper)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    // PacketEvents - optional soft dependency for glowing entity support
    compileOnly(libs.packetevents.spigot)

    // WorldGuard - optional soft dependency for protection checking
    compileOnly(libs.worldguard.bukkit)

    // Lands - optional soft dependency for protection checking
    compileOnly(libs.lands.api)

    // Residence - optional soft dependency for protection checking
    compileOnly(libs.residence.coordinate()) {
        exclude(group = "org.dynmap", module = "dynmap-api")
    }

    // GriefPrevention - optional soft dependency for protection checking
    compileOnly(libs.griefprevention)

    // Testing - Kotest
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Paper API and Adventure for tests (compileOnly for main, so not inherited)
    testImplementation(libs.paper.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.packetevents.spigot)
}

// Runtime libraries are compiled and tested against the exact versions the
// server will fetch.
configurations.compileOnly.get().extendsFrom(paperLibrary)
configurations.testImplementation.get().extendsFrom(paperLibrary)

// =============================================================================
// Generated library-loader manifest
// =============================================================================

/** Writes the `paperLibrary` coordinates into a resource the plugin loader reads. */
abstract class GeneratePaperLibraries : DefaultTask() {
    @get:Input
    abstract val coordinates: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDirectory.get().file(RESOURCE_PATH).asFile
        target.parentFile.mkdirs()
        target.writeText(
            buildString {
                appendLine("# Generated by :generatePaperLibraries - do not edit.")
                appendLine("# Source of truth: gradle/libs.versions.toml")
                coordinates.get().forEach { appendLine(it) }
            },
        )
    }

    companion object {
        const val RESOURCE_PATH = "me/cyljacky02/loafylib/libraries.txt"
    }
}

val generatePaperLibraries by tasks.registering(GeneratePaperLibraries::class) {
    description = "Writes the Paper library-loader manifest from the version catalog."
    coordinates.set(paperLibraries.map { it.coordinate() })
    outputDirectory.set(layout.buildDirectory.dir("generated/paper-libraries"))
}

sourceSets.main {
    resources.srcDir(generatePaperLibraries.flatMap { it.outputDirectory })
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xemit-jvm-type-annotations")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val placeholders = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to pluginApiVersion,
    )
    inputs.properties(placeholders)
    filesMatching("paper-plugin.yml") {
        expand(placeholders)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Minimize JAR - only include classes actually used
    minimize {
        // Don't minimize Lettuce - it uses reflection and service loading
        exclude(dependency("io.lettuce:lettuce-core:.*"))
        // Don't minimize netty-resolver-dns - required for Lettuce's static initializer
        exclude(dependency("io.netty:netty-resolver-dns:.*"))
        // Don't minimize InvUI - it uses reflection for NMS access
        exclude(dependency("xyz.xenondevs.invui:.*"))
    }

    // Relocations
    // Lettuce relocated (most Netty excluded - uses Paper's native Netty)
    relocate("io.lettuce", "me.cyljacky02.loafylib.libs.lettuce")
    relocate("redis.clients", "me.cyljacky02.loafylib.libs.redis.clients")

    // Relocate netty-resolver-dns to avoid conflicts with Paper's Netty
    // This is required because Lettuce's DefaultClientResources static initializer
    // references DnsAddressResolverGroup and DefaultDnsCnameCache
    relocate("io.netty.resolver.dns", "me.cyljacky02.loafylib.libs.netty.resolver.dns")

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
    minecraftVersion(minecraftVersion)
}

// =============================================================================
// Publishing — consumed by the sibling Loafy plugins via GitHub Packages.
// =============================================================================
publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifactId = "loafy-lib"
            artifact(tasks.shadowJar)
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cyljacky02/loafy-lib")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

// =============================================================================
// CI helpers — let workflows read the resolved versions without parsing TOML.
// =============================================================================
tasks.register("printMinecraftVersion") {
    description = "Prints the Minecraft version derived from the paper-api coordinate."
    val value = minecraftVersion
    doLast { println(value) }
}

tasks.register("printPaperApiVersion") {
    description = "Prints the full paper-api artifact version."
    val value = paperApiVersion
    doLast { println(value) }
}

tasks.register("printProjectVersion") {
    description = "Prints the plugin version."
    val value = project.version.toString()
    doLast { println(value) }
}
