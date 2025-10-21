plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization") version "1.8.22"
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
                
                // Kotlin coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Kotlin serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
                
                // Ktor
                implementation("io.ktor:ktor-client-core:2.3.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                
                // JNA for Windows API access
                implementation("net.java.dev.jna:jna:5.13.0")
                implementation("net.java.dev.jna:jna-platform:5.13.0")
                
                // Ktor server
                implementation("io.ktor:ktor-server-core:2.3.3")
                implementation("io.ktor:ktor-server-netty:2.3.3")
                implementation("io.ktor:ktor-client-okhttp:2.3.3")
                
                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.7")
            }
        }
    }
}


