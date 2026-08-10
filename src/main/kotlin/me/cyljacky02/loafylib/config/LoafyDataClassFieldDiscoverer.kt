package me.cyljacky02.loafylib.config

import io.leangen.geantyref.GenericTypeReflector
import io.leangen.geantyref.GenericTypeReflector.erase
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.AnnotatedType
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType
import org.spongepowered.configurate.objectmapping.FieldDiscoverer
import org.spongepowered.configurate.util.Types.combinedAnnotations

/**
 * A Kotlin data class field discoverer that derives the field type from the property's getter
 * return type (which retains generic signature information like List<String>), rather than the
 * primary constructor parameter type (which may be erased to a raw type under newer Kotlin
 * compilers).
 *
 * This avoids Configurate's "Raw types are not supported for collections" error when mapping
 * Kotlin data class properties like `val allowedSpawnReasons: List<String>`.
 */
internal object LoafyDataClassFieldDiscoverer : FieldDiscoverer<MutableMap<KParameter, Any?>> {
    override fun <V> discover(
        target: AnnotatedType,
        collector: FieldDiscoverer.FieldCollector<MutableMap<KParameter, Any?>, V>,
    ): FieldDiscoverer.InstanceFactory<MutableMap<KParameter, Any?>>? {
        val klass = erase(target.type).kotlin
        if (!klass.isData) {
            return null
        }

        val constructor = klass.primaryConstructor ?: return null
        // Make accessible for private classes
        constructor.isAccessible = true

        val properties = klass.memberProperties

        constructor.parameters.forEach { param ->
            val name = param.name ?: return@forEach
            val property = properties.firstOrNull { it.name == name } ?: return@forEach

            @Suppress("UNCHECKED_CAST")
            val prop = property as KProperty1<V, *>
            prop.isAccessible = true
            // For value classes (e.g., Duration), javaType returns the underlying primitive (long),
            // but Kotlin reflection's get() returns the boxed value class. Use the classifier's
            // javaObjectType for value classes to get the correct type for serializer lookup.
            val returnType = prop.returnType
            val classifier = returnType.classifier
            val kotlinType = if (classifier is KClass<*> && classifier.isValue) {
                classifier.javaObjectType
            } else {
                returnType.javaType
            }
            val resolvedType = GenericTypeReflector.annotate(
                GenericTypeReflector.resolveType(kotlinType, target.type)
            )
            
            val javaGetter = prop.javaGetter

            val annotationElements = buildList<AnnotatedElement> {
                add(param.type.javaElement)
                add(param.javaElement)
                property.javaField?.let(::add)
                javaGetter?.let(::add)
            }

            @Suppress("UNCHECKED_CAST")
            collector.accept(
                name,
                resolvedType,
                combinedAnnotations(*annotationElements.toTypedArray()),
                // deserializer
                { intermediate, arg, implicitProvider ->
                    if (arg != null) {
                        intermediate[param] = arg
                    } else if (!param.isOptional) {
                        intermediate[param] = implicitProvider.get()
                    }
                },
                // serializer
                { prop.get(it) },
            )
        }

        return object : FieldDiscoverer.InstanceFactory<MutableMap<KParameter, Any?>> {
            override fun begin(): MutableMap<KParameter, Any?> = mutableMapOf()
            override fun complete(intermediate: MutableMap<KParameter, Any?>): Any = constructor.callBy(intermediate)
            override fun canCreateInstances(): Boolean = true
        }
    }
}

// Kotlin reflection elements don't always have a direct Java backing element (e.g., KType, KParameter).
// This wrapper allows their annotations to be included via AnnotatedElement.
private class WrappedElement(private val backing: KAnnotatedElement) : AnnotatedElement {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Annotation> getAnnotation(annotationClass: Class<T>): T? {
        return backing.annotations.firstOrNull { it.annotationClass.java == annotationClass } as T?
    }

    override fun getAnnotations(): Array<Annotation> = backing.annotations.toTypedArray()
    override fun getDeclaredAnnotations(): Array<Annotation> = annotations
}

/** Get a Kotlin annotated element as a Java one (best-effort). */
private val KAnnotatedElement.javaElement: AnnotatedElement
    get() {
        if (this is KProperty<*>) {
            val javaType = this.javaField ?: this.javaGetter
            if (javaType != null) {
                return javaType
            }
        } else if (this is KFunction<*>) {
            val javaType = this.javaMethod ?: this.javaConstructor
            if (javaType != null) {
                return javaType
            }
        }

        return WrappedElement(this)
    }
