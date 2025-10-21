import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi) // Windows 安装包
            packageName = "Saki-Mahjong-Assistant"
            packageVersion = "0.1.0"
            
            windows {
                menuGroup = "Saki"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}
