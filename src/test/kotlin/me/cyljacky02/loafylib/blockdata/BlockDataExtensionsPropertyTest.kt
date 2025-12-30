package me.cyljacky02.loafylib.blockdata

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * Property-based tests for BlockData extension functions.
 *
 * Tests focus on the extension functions that provide convenient
 * access to BlockDataService functionality directly on Block objects.
 */
class BlockDataExtensionsPropertyTest : FunSpec({

    context("Block.hasBlockData Extension") {
        /**
         * The extension function should delegate correctly to chunk PDC check.
         */
        test("hasBlockData extension matches direct PDC check") {
            checkAll(100, Arb.int(0..15), Arb.int(-64..320), Arb.int(0..15)) { relX, y, relZ ->
                val plugin = mockk<Plugin> {
                    every { name } returns "testplugin"
                    every { namespace() } returns "testplugin"
                }
                
                val chunkPdc = mockk<PersistentDataContainer> {
                    every { has(any<NamespacedKey>(), eq(PersistentDataType.TAG_CONTAINER)) } returns true
                }
                
                val chunk = mockk<Chunk> { every { persistentDataContainer } returns chunkPdc }
                val block = mockk<Block> {
                    every { x } returns relX
                    every { this@mockk.y } returns y
                    every { z } returns relZ
                    every { this@mockk.chunk } returns chunk
                }
                
                // Extension function should return true when chunk PDC has the key
                block.hasBlockData(plugin) shouldBe true
            }
        }
    }

    context("Namespace Isolation via NamespacedKey") {
        /**
         * Different plugins get different block keys due to namespace.
         * This tests that our key generation correctly uses plugin namespace.
         */
        test("different plugins produce different block keys for same coordinates") {
            val plugin1 = mockk<Plugin> {
                every { name } returns "plugin1"
                every { namespace() } returns "plugin1"
            }
            
            val plugin2 = mockk<Plugin> {
                every { name } returns "plugin2"
                every { namespace() } returns "plugin2"
            }
            
            val block = mockk<Block> {
                every { x } returns 100  // relX = 100 & 0xF = 4
                every { y } returns 64
                every { z } returns 200  // relZ = 200 & 0xF = 8
            }
            
            val key1 = BlockPDC.createBlockKey(block, plugin1)
            val key2 = BlockPDC.createBlockKey(block, plugin2)
            
            // Same coordinates produce same hex key (relX=4, y=64, relZ=8)
            // packed = (4 << 16) | (8 << 12) | (64 + 2048) = 0x48840
            key1.key shouldBe key2.key  // "48840" (hex-encoded packed coords)
            key1.namespace shouldBe "plugin1"
            key2.namespace shouldBe "plugin2"
            
            // Full keys are different due to namespace
            key1.toString() shouldBe "plugin1:48840"
            key2.toString() shouldBe "plugin2:48840"
        }
    }

    context("Block.isBlockDataProtected Extension") {
        /**
         * The extension function should read protection status directly
         * from chunk PDC without creating a BlockPDC wrapper.
         */
        test("isBlockDataProtected returns false when no data exists") {
            val plugin = mockk<Plugin> {
                every { name } returns "testplugin"
                every { namespace() } returns "testplugin"
            }
            
            val chunkPdc = mockk<PersistentDataContainer> {
                every { get(any<NamespacedKey>(), eq(PersistentDataType.TAG_CONTAINER)) } returns null
            }
            
            val chunk = mockk<Chunk> { every { persistentDataContainer } returns chunkPdc }
            val block = mockk<Block> {
                every { x } returns 5
                every { y } returns 64
                every { z } returns 10
                every { this@mockk.chunk } returns chunk
            }
            
            block.isBlockDataProtected(plugin) shouldBe false
        }
        
        test("isBlockDataProtected returns true when protection flag is set") {
            val plugin = mockk<Plugin> {
                every { name } returns "testplugin"
                every { namespace() } returns "testplugin"
            }
            
            val blockPdcContainer = mockk<PersistentDataContainer> {
                every { has(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) } returns true
                every { get(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) } returns 1.toByte()
            }
            
            val chunkPdc = mockk<PersistentDataContainer> {
                every { get(any<NamespacedKey>(), eq(PersistentDataType.TAG_CONTAINER)) } returns blockPdcContainer
            }
            
            val chunk = mockk<Chunk> { every { persistentDataContainer } returns chunkPdc }
            val block = mockk<Block> {
                every { x } returns 5
                every { y } returns 64
                every { z } returns 10
                every { this@mockk.chunk } returns chunk
            }
            
            block.isBlockDataProtected(plugin) shouldBe true
        }
    }
})
