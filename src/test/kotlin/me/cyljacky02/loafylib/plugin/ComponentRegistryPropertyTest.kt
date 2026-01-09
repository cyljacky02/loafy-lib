package me.cyljacky02.loafylib.plugin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.reflect.KClass

/**
 * Property-based tests for ComponentRegistry.
 *
 * Tests topological initialization order, reverse shutdown order,
 * circular dependency detection, and graceful failure handling.
 */
class ComponentRegistryPropertyTest : FunSpec({

    context("Topological initialization order") {
        
        test("Linear chain: dependencies are initialized before dependents") {
            val initOrder = mutableListOf<String>()
            
            // Create a linear chain: A <- B <- C <- D
            val componentA = ComponentA(initOrder)
            val componentB = ComponentB(initOrder)
            val componentC = ComponentC(initOrder)
            val componentD = ComponentD(initOrder)
            
            val registry = ComponentRegistry()
            
            // Register in reverse order to ensure topological sort works
            registry.register(ComponentD::class, componentD)
            registry.register(ComponentC::class, componentC)
            registry.register(ComponentB::class, componentB)
            registry.register(ComponentA::class, componentA)
            
            registry.initializeAll()
            
            // A must be first (no dependencies), then B, C, D
            initOrder shouldBe listOf("A", "B", "C", "D")
        }

        test("Diamond dependency pattern initializes correctly") {
            val initOrder = mutableListOf<String>()
            
            // Diamond: Top depends on Left and Right, both depend on Bottom
            val bottom = DiamondBottom(initOrder)
            val left = DiamondLeft(initOrder)
            val right = DiamondRight(initOrder)
            val top = DiamondTop(initOrder)
            
            val registry = ComponentRegistry()
            registry.register(DiamondTop::class, top)
            registry.register(DiamondLeft::class, left)
            registry.register(DiamondRight::class, right)
            registry.register(DiamondBottom::class, bottom)
            
            registry.initializeAll()
            
            // Bottom must be first
            initOrder.first() shouldBe "Bottom"
            // Top must be last
            initOrder.last() shouldBe "Top"
            // Left and Right must be after Bottom but before Top
            initOrder.subList(1, 3) shouldContainAll listOf("Left", "Right")
        }
    }


    context("Reverse shutdown order") {
        
        test("Shutdown order is reverse of initialization order") {
            val initOrder = mutableListOf<String>()
            val shutdownOrder = mutableListOf<String>()
            
            val componentA = ShutdownTrackingA(initOrder, shutdownOrder)
            val componentB = ShutdownTrackingB(initOrder, shutdownOrder)
            val componentC = ShutdownTrackingC(initOrder, shutdownOrder)
            
            val registry = ComponentRegistry()
            registry.register(ShutdownTrackingC::class, componentC)
            registry.register(ShutdownTrackingB::class, componentB)
            registry.register(ShutdownTrackingA::class, componentA)
            
            registry.initializeAll()
            registry.shutdownAll()
            
            // Shutdown order should be exact reverse of init order
            shutdownOrder shouldBe initOrder.reversed()
        }
    }

    context("Circular dependency detection") {
        
        test("Direct circular dependency (A -> B -> A) throws IllegalStateException") {
            val componentA = CircularComponentA()
            val componentB = CircularComponentB()
            
            val registry = ComponentRegistry()
            registry.register(CircularComponentA::class, componentA)
            registry.register(CircularComponentB::class, componentB)
            
            val exception = shouldThrow<IllegalStateException> {
                registry.initializeAll()
            }
            exception.message shouldContain "Circular dependency"
        }

        test("Indirect circular dependency (A -> B -> C -> A) throws IllegalStateException") {
            val componentA = CircularComponent3A()
            val componentB = CircularComponent3B()
            val componentC = CircularComponent3C()
            
            val registry = ComponentRegistry()
            registry.register(CircularComponent3A::class, componentA)
            registry.register(CircularComponent3B::class, componentB)
            registry.register(CircularComponent3C::class, componentC)
            
            val exception = shouldThrow<IllegalStateException> {
                registry.initializeAll()
            }
            exception.message shouldContain "Circular dependency"
        }

        test("Self-referencing component throws IllegalStateException") {
            val component = SelfReferencingComponent()
            
            val registry = ComponentRegistry()
            registry.register(SelfReferencingComponent::class, component)
            
            val exception = shouldThrow<IllegalStateException> {
                registry.initializeAll()
            }
            exception.message shouldContain "Circular dependency"
        }
    }

    context("Graceful failure rollback") {
        
        test("When middle component fails, earlier components are shutdown in reverse order") {
            val initOrder = mutableListOf<String>()
            val shutdownOrder = mutableListOf<String>()
            
            // Chain: A <- B <- FailingC <- D
            val componentA = FailureTestA(initOrder, shutdownOrder)
            val componentB = FailureTestB(initOrder, shutdownOrder)
            val failingC = FailureTestFailingC(initOrder, shutdownOrder)
            val componentD = FailureTestD(initOrder, shutdownOrder)
            
            val registry = ComponentRegistry()
            registry.register(FailureTestA::class, componentA)
            registry.register(FailureTestB::class, componentB)
            registry.register(FailureTestFailingC::class, failingC)
            registry.register(FailureTestD::class, componentD)
            
            shouldThrow<RuntimeException> {
                registry.initializeAll()
            }
            
            // A and B should have been initialized
            initOrder shouldBe listOf("A", "B")
            
            // A and B should be shutdown in reverse order
            shutdownOrder shouldBe listOf("B", "A")
        }

        test("When first component fails, no shutdown occurs") {
            val initOrder = mutableListOf<String>()
            val shutdownOrder = mutableListOf<String>()
            
            val failingFirst = FailingFirstComponent(initOrder, shutdownOrder)
            val componentB = FailureTestAfterFirst(initOrder, shutdownOrder)
            
            val registry = ComponentRegistry()
            registry.register(FailingFirstComponent::class, failingFirst)
            registry.register(FailureTestAfterFirst::class, componentB)
            
            shouldThrow<RuntimeException> {
                registry.initializeAll()
            }
            
            // Nothing should have been initialized or shutdown
            initOrder shouldBe emptyList()
            shutdownOrder shouldBe emptyList()
        }
    }
})


// =============================================================================
// Linear Chain Components (A <- B <- C <- D)
// =============================================================================

private class ComponentA(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = emptyList<KClass<out PluginComponent>>()
    override suspend fun initialize() { initOrder.add("A") }
    override suspend fun shutdown() {}
}

private class ComponentB(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = listOf(ComponentA::class)
    override suspend fun initialize() { initOrder.add("B") }
    override suspend fun shutdown() {}
}

private class ComponentC(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = listOf(ComponentB::class)
    override suspend fun initialize() { initOrder.add("C") }
    override suspend fun shutdown() {}
}

private class ComponentD(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = listOf(ComponentC::class)
    override suspend fun initialize() { initOrder.add("D") }
    override suspend fun shutdown() {}
}

// =============================================================================
// Diamond Pattern Components
// =============================================================================

private class DiamondBottom(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = emptyList<KClass<out PluginComponent>>()
    override suspend fun initialize() { initOrder.add("Bottom") }
    override suspend fun shutdown() {}
}

private class DiamondLeft(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = listOf(DiamondBottom::class)
    override suspend fun initialize() { initOrder.add("Left") }
    override suspend fun shutdown() {}
}

private class DiamondRight(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = listOf(DiamondBottom::class)
    override suspend fun initialize() { initOrder.add("Right") }
    override suspend fun shutdown() {}
}

private class DiamondTop(private val initOrder: MutableList<String>) : PluginComponent {
    override fun dependencies() = listOf(DiamondLeft::class, DiamondRight::class)
    override suspend fun initialize() { initOrder.add("Top") }
    override suspend fun shutdown() {}
}

// =============================================================================
// Shutdown Tracking Components (Linear Chain)
// =============================================================================

private class ShutdownTrackingA(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = emptyList<KClass<out PluginComponent>>()
    override suspend fun initialize() { initOrder.add("A") }
    override suspend fun shutdown() { shutdownOrder.add("A") }
}

private class ShutdownTrackingB(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = listOf(ShutdownTrackingA::class)
    override suspend fun initialize() { initOrder.add("B") }
    override suspend fun shutdown() { shutdownOrder.add("B") }
}

private class ShutdownTrackingC(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = listOf(ShutdownTrackingB::class)
    override suspend fun initialize() { initOrder.add("C") }
    override suspend fun shutdown() { shutdownOrder.add("C") }
}


// =============================================================================
// Circular Dependency Components
// =============================================================================

private class CircularComponentA : PluginComponent {
    override fun dependencies() = listOf(CircularComponentB::class)
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}

private class CircularComponentB : PluginComponent {
    override fun dependencies() = listOf(CircularComponentA::class)
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}

// 3-way circular dependency
private class CircularComponent3A : PluginComponent {
    override fun dependencies() = listOf(CircularComponent3B::class)
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}

private class CircularComponent3B : PluginComponent {
    override fun dependencies() = listOf(CircularComponent3C::class)
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}

private class CircularComponent3C : PluginComponent {
    override fun dependencies() = listOf(CircularComponent3A::class)
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}

// Self-referencing component
private class SelfReferencingComponent : PluginComponent {
    override fun dependencies() = listOf(SelfReferencingComponent::class)
    override suspend fun initialize() {}
    override suspend fun shutdown() {}
}

// =============================================================================
// Failure Test Components
// =============================================================================

private class FailureTestA(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = emptyList<KClass<out PluginComponent>>()
    override suspend fun initialize() { initOrder.add("A") }
    override suspend fun shutdown() { shutdownOrder.add("A") }
}

private class FailureTestB(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = listOf(FailureTestA::class)
    override suspend fun initialize() { initOrder.add("B") }
    override suspend fun shutdown() { shutdownOrder.add("B") }
}

private class FailureTestFailingC(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = listOf(FailureTestB::class)
    override suspend fun initialize() { throw RuntimeException("FailingC failed to initialize") }
    override suspend fun shutdown() { shutdownOrder.add("C") }
}

private class FailureTestD(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = listOf(FailureTestFailingC::class)
    override suspend fun initialize() { initOrder.add("D") }
    override suspend fun shutdown() { shutdownOrder.add("D") }
}

private class FailingFirstComponent(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = emptyList<KClass<out PluginComponent>>()
    override suspend fun initialize() { throw RuntimeException("First component failed") }
    override suspend fun shutdown() { shutdownOrder.add("First") }
}

private class FailureTestAfterFirst(
    private val initOrder: MutableList<String>,
    private val shutdownOrder: MutableList<String>
) : PluginComponent {
    override fun dependencies() = listOf(FailingFirstComponent::class)
    override suspend fun initialize() { initOrder.add("AfterFirst") }
    override suspend fun shutdown() { shutdownOrder.add("AfterFirst") }
}
