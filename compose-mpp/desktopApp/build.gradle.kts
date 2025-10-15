import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

// 添加任务将mitm_script.py复制到输出目录
tasks.register<Copy>("copyMitmScript") {
    from(rootProject.file("mitm_script.py"))
    into(layout.buildDirectory.dir("libs"))
}

// 确保在构建应用之前复制脚本
tasks.named("build") {
    dependsOn("copyMitmScript")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MahjongCopilot"
            packageVersion = "1.0.0"
        }
    }
}