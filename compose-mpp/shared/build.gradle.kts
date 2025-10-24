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
                // 添加Coroutines依赖
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                // 添加Ktor网络库依赖
                implementation("io.ktor:ktor-network:2.3.3")
                implementation("io.ktor:ktor-utils:2.3.3")
            }
        }
    }
}


