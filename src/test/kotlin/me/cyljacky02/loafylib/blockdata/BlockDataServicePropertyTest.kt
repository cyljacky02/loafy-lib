package me.cyljacky02.loafylib.blockdata

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
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
 * Property-based tests for BlockDataService.
 *
 * Tests focus on verifiable properties of the service implementation
 * that can be tested with mocked Bukkit components.
 */
class BlockDataServicePropertyTest : FunSpec({

    context("Existence Check") {
        /**
         * For any block coordinates, hasBlockData should accurately reflect
         * whether the chunk PDC contains an entry for that block.
         */
        test("hasBlockData accurately reflects chunk PDC state") {
            checkAll(100, Arb.int(-30000000..30000000), Arb.int(-64..320), Arb.int(-30000000..30000000), Arb.boolean()) { x, y, z, hasEntry ->
                val plugin = mockk<Plugin> {
                    every { name } returns "testplugin"
                    every { namespace() } returns "testplugin"
                }
                val chunkPdc = mockk<PersistentDataContainer> {
                    every { has(any<NamespacedKey>(), eq(PersistentDataType.TAG_CONTAINER)) } returns hasEntry
                }
                val chunk = mockk<Chunk> { every { persistentDataContainer } returns chunkPdc }
                val block = mockk<Block> {
                    every { this@mockk.x } returns x
                    every { this@mockk.y } returns y
                    every { this@mockk.z } returns z
                    every { this@mockk.chunk } returns chunk
                }
                val loafyPlugin = mockk<Plugin> { every { isEnabled } returns true }
                val service = BlockDataServiceImpl(loafyPlugin)
                
                service.hasBlockData(block, plugin) shouldBe hasEntry
            }
        }
    }

    context("Protection Status") {
        /**
         * isProtected reads directly from chunk PDC without creating BlockPDC wrapper.
         * This tests the optimized path that avoids unnecessary object creation.
         */
        test("isProtected reads directly from nested PDC structure") {
            checkAll(100, Arb.int(0..15), Arb.int(-64..320), Arb.int(0..15), Arb.boolean()) { relX, y, relZ, isProtected ->
                val plugin = mockk<Plugin> {
                    every { name } returns "testplugin"
                    every { namespace() } returns "testplugin"
                }
                
                // Mock the nested PDC structure (chunk PDC -> block PDC)
                val blockPdcContainer = mockk<PersistentDataContainer> {
                    every { has(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) } returns isProtected
                    every { get(BlockDataKeys.PROTECTED, PersistentDataType.BYTE) } returns if (isProtected) 1.toByte() else null
                }
                
                val chunkPdc = mockk<PersistentDataContainer> {
                    every { get(any<NamespacedKey>(), eq(PersistentDataType.TAG_CONTAINER)) } returns blockPdcContainer
                }
                
                val chunk = mockk<Chunk> { every { persistentDataContainer } returns chunkPdc }
                val block = mockk<Block> {
                    every { x } returns relX
                    every { this@mockk.y } returns y
                    every { z } returns relZ
                    every { this@mockk.chunk } returns chunk
                }
                
                val loafyPlugin = mockk<Plugin> { every { isEnabled } returns true }
                val service = BlockDataServiceImpl(loafyPlugin)
                
                service.isProtected(block, plugin) shouldBe isProtected
            }
        }
        
        test("isProtected returns false when block has no data") {
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
            
            val loafyPlugin = mockk<Plugin> { every { isEnabled } returns true }
            val service = BlockDataServiceImpl(loafyPlugin)
            
            service.isProtected(block, plugin) shouldBe false
        }
    }
})
