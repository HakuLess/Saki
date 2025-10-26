plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                // Coroutines for async flow collection and UI events
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                // BrowserUp Proxy (MITM) via Maven Central instead of JitPack
                implementation("com.github.valfirst.browserup-proxy:browserup-proxy-core:3.2.2")
                implementation("com.github.valfirst.browserup-proxy:browserup-proxy-mitm:3.2.2")
                implementation("org.slf4j:slf4j-simple:2.0.12")
            }
        }
    }
}


