plugins {
    // 0.9.0 references JvmVendorSpec.IBM_SEMERU, which Gradle 9 removed. It only
    // fails once a toolchain actually has to be provisioned, so the breakage
    // stayed hidden until the JDK version changed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "LoafyLib"
