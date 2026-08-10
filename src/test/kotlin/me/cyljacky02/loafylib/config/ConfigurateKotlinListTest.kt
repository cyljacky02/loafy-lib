package me.cyljacky02.loafylib.config

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import java.nio.file.Files

@ConfigSerializable
private data class TestRootConfig(
    @Comment("Anti-farm controls")
    val antiFarm: TestAntiFarmConfig = TestAntiFarmConfig(),
)

@ConfigSerializable
private data class TestAntiFarmConfig(
    @Comment("Spawn reasons that are eligible")
    val allowedSpawnReasons: List<String> = listOf(
        "NATURAL",
        "RAID",
    ),
)

class ConfigurateKotlinListTest : FunSpec({
    test("Kotlin data class List<String> properties can be default-saved without raw type errors") {
        val dir = Files.createTempDirectory("loafylib-configurate")
        val path = dir.resolve("config.yml")

        shouldNotThrowAny {
            val loader = ConfigurateUtils.createYamlLoader(path)
            loader.loadAndSaveDefaults(TestRootConfig())
        }
    }
})
