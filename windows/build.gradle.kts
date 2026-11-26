import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

group = "com.maluku.teyfey"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("com.google.code.gson:gson:2.11.0")
    // Video: VLC (must be installed on the PC, 64-bit)
    implementation("uk.co.caprica:vlcj:4.8.2")
    // Images
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.maluku.teyfey.MainKt"
        jvmArgs += listOf("-Xmx2g", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Teyfey"
            // MSI needs a higher version each build, otherwise it won't install over the old one
            packageVersion = "1.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
            vendor = "MALUKU"
            description = "Teyfey IPTV-speler"
            includeAllModules = true
            // VLC (libvlc + plugins) is copied in here by the GitHub workflow
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appresources"))
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "Teyfey"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6b7d2f1e-3c9a-4e55-9a7c-2f1d8c0e7a41"
            }
        }
    }
}
