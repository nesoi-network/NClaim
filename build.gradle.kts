import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    `maven-publish`
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "nesoi.aysihuniks"
version = "4.0.0"
description = "Advanced chunk claim system for Paper and Folia"

val paperApi: String = providers.gradleProperty("paperApi").get()
// Bytecode level stays at 21 (Minecraft 1.21.x requirement). The toolchain may be newer,
// e.g. `-PjavaToolchain=25` is needed to compile against the 26.x API jars.
val javaToolchain: Int = providers.gradleProperty("javaToolchain").map(String::toInt).getOrElse(21)

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaToolchain)
    withSourcesJar()
}

// The 26.x API is published for Java 25 only; let the compile classpath accept it while the
// produced bytecode still targets 21. Used for the compatibility build against newer servers.
configurations.compileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaToolchain) }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.artillex-studios.com/releases/")
    maven("https://jitpack.io") { content { includeGroup("com.github.MilkBowl") } }
}

dependencies {
    // Nothing is shaded: everything comes from the server, another plugin,
    // or Paper's library loader (see `libraries` in plugin.yml).
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    // Only the API modules, without transitives: WorldGuard pins strict Guava/FastUtil versions
    // that would conflict with newer Paper APIs, and the server provides those libraries anyway.
    listOf(
        "com.sk89q.worldguard:worldguard-bukkit:7.0.14",
        "com.sk89q.worldguard:worldguard-core:7.0.14",
        "com.sk89q.worldedit:worldedit-bukkit:7.3.9",
        "com.sk89q.worldedit:worldedit-core:7.3.9",
    ).forEach { compileOnly(it) { isTransitive = false } }
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.8.2")
    compileOnly("com.artillexstudios:AxSellwands:1.11.0") { isTransitive = false }

    // Compile-time only (generates getters/setters); not part of the jar.
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
}

tasks {
    compileJava {
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    processResources {
        val tokens = mapOf("version" to project.version.toString())
        inputs.properties(tokens)
        filteringCharset = "UTF-8"
        filesMatching("**/*.yml") {
            filter<ReplaceTokens>("tokens" to tokens)
        }
    }

    jar {
        archiveFileName = "NClaim-${project.version}.jar"
    }

    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Dcom.mojang.eula.agree=true", "-DPaper.IgnoreJavaVersion=true")
    }
}

runPaper.folia.registerTask {
    minecraftVersion("1.21.11")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
