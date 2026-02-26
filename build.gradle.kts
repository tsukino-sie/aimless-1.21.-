val pluginGroup: String by project
val pluginName: String by project
val pluginVersion: String by project
val pluginMain: String by project
plugins {
    // kotlin 1.3 -> 2.0.20 업데이트
    kotlin("jvm") version "2.0.20"
}

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    // Paper 저장소 주소가 변경됨에 따른 수정
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/") // ProtocolLib
    maven("https://jitpack.io/")
    maven("https://libraries.minecraft.net/")
}

dependencies {
    // Kotlin 표준 라이브러리 (최신 버전 수정)
    implementation(kotlin("stdlib"))
    testImplementation("junit:junit:4.13.1")

    // Paper API (그룹 이름이 com.destroystokyo에서 io.papermc로 변경됨에 따른 수정)
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // ProtocolLib 1.21.x 지원 버전(5.3.0)으로 업데이트
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    compileOnly("com.mojang:authlib:6.0.54")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    javadoc {
        options.encoding = "UTF-8"
    }
    processResources {
        val props = mapOf(
            "pluginName" to pluginName,
            "pluginVersion" to pluginVersion,
            "pluginMain" to pluginMain
        )
        inputs.properties(props)
        filesMatching("**/*.yml") {
            expand(props)
        }
    }
}