package me.cyljacky02.loafylib;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Paper PluginLoader that loads dependencies via Paper's library loader.
 * 
 * IMPORTANT: This class must be Java, not Kotlin, because it runs before the
 * library loader
 * has loaded Kotlin stdlib.
 * 
 * LoafyLib provides shared dependencies for all Loafy plugins:
 * - Kotlin stdlib and coroutines
 * - HikariCP for database connection pooling
 * - MariaDB JDBC driver
 * - Configurate for YAML/HOCON configuration
 * - Reactor Core for Lettuce reactive streams
 * 
 * Shaded dependencies (in JAR, not loaded here):
 * - Lettuce (Redis client) - relocated to avoid classloader conflicts
 * - InvUI (GUI framework) - NOT relocated (uses Paper's native Netty)
 * - Lamp (command framework) - relocated
 */
@SuppressWarnings("UnstableApiUsage")
public class LoafyLibPluginLoader implements PluginLoader {

        @Override
        public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
                MavenLibraryResolver resolver = new MavenLibraryResolver();

                // Add Maven Central mirror (required by Paper ToS)
                resolver.addRepository(
                                new RemoteRepository.Builder("central", "default",
                                                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)
                                                .build());

                // Add Sponge repository for Configurate
                resolver.addRepository(
                                new RemoteRepository.Builder("sponge", "default",
                                                "https://repo.spongepowered.org/repository/maven-public/")
                                                .build());

                // === Kotlin ===
                // Kotlin stdlib and coroutines (must match compile version)
                resolver.addDependency(
                                new Dependency(new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:2.3.10"), null));
                // kotlin-reflect is required by configurate-extra-kotlin to instantiate Kotlin data classes
                // using default constructor arguments.
                resolver.addDependency(
                                new Dependency(new DefaultArtifact("org.jetbrains.kotlin:kotlin-reflect:2.3.10"), null));
                resolver.addDependency(
                                new Dependency(new DefaultArtifact(
                                                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"), null));
                // Kotlinx Serialization - for Redis sync payloads and general serialization
                resolver.addDependency(
                                new Dependency(new DefaultArtifact(
                                                "org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0"), null));
                resolver.addDependency(
                                new Dependency(new DefaultArtifact(
                                                "org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0"), null));

                // === Database ===
                // HikariCP for connection pooling
                resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:7.0.2"), null));
                // MariaDB JDBC driver
                resolver.addDependency(new Dependency(new DefaultArtifact("org.mariadb.jdbc:mariadb-java-client:3.5.7"),
                                null));
                // SQLite JDBC driver (for local file-based databases)
                resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.47.2.0"), null));

                // === Configuration ===
                // Configurate for YAML/HOCON configuration
                resolver.addDependency(
                                new Dependency(new DefaultArtifact(
                                                "org.spongepowered:configurate-hocon:4.2.0"), null));
                resolver.addDependency(
                                new Dependency(new DefaultArtifact("org.spongepowered:configurate-yaml:4.2.0"),
                                                null));
                resolver.addDependency(
                                new Dependency(new DefaultArtifact(
                                                "org.spongepowered:configurate-extra-kotlin:4.2.0"), null));

                // === Redis Support ===
                // Reactor Core - required by Lettuce for reactive streams (not provided by
                // Paper)
                // Lettuce itself is SHADED into the JAR (not loaded here) to avoid classloader
                // conflicts
                resolver.addDependency(
                                new Dependency(new DefaultArtifact("io.projectreactor:reactor-core:3.8.1"), null));

                // NOTE: We intentionally DO NOT load netty-resolver-dns here!
                // Paper already has Netty loaded. If we load netty-resolver-dns, Lettuce's
                // DefaultClientResources static initializer creates DnsAddressResolverGroup
                // which
                // triggers DnsNameResolver.<clinit> to register AttributeKey
                // "io.netty.resolver.dns.pipeline".
                // Paper's Netty already registered this key → IllegalArgumentException.
                //
                // Without netty-resolver-dns on classpath, Lettuce falls back to using
                // DefaultAddressResolverGroup.INSTANCE (JDK's blocking DNS resolver).
                // This is configured in LettuceRedisManager via:
                // DefaultClientResources.builder().addressResolverGroup(DefaultAddressResolverGroup.INSTANCE)

                classpathBuilder.addLibrary(resolver);
        }
}
