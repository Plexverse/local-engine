import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`
    id("com.mineplex.sdk.plugin") version "1.21.10"
    id("com.gradleup.shadow") version "9.1.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Mineplex BOM for dependency management
    implementation(platform(libs.mineplex.bom))

    // MongoDB driver for DataStorageModule
    implementation(libs.mongodb.driver.sync)
    // PostgreSQL JDBC driver for ManagedDBModule
    implementation(libs.postgresql)
    // MySQL JDBC driver for ManagedDBModule
    implementation(libs.mysql)
    // HikariCP connection pool for ManagedDBModule
    implementation(libs.hikaricp)
    // Caffeine cache for key field caching
    implementation(libs.caffeine)
    // Kafka clients for MessagingModule
    implementation(libs.kafka.clients)
    // Jackson YAML (only YAML, core Jackson comes from SDK)
    implementation(libs.jackson.yaml)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

group = "net.plexverse.enginebridge"
version = "1.2.6"
description = "engine-bridge"

tasks {
    build {
        dependsOn(withType<ShadowJar>())
    }

    processResources {
        filesMatching("plugin.yml") {
            filter { line ->
                line.replace("@version@", project.version.toString())
            }
        }
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("all-local")
        isZip64 = true
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())

        // Include Mineplex SDK classes from compile classpath
        project.configurations.compileClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.group.startsWith("com.mineplex") }
            .forEach { artifact ->
                if (artifact.file.exists() && artifact.file.extension == "jar") {
                    from(project.zipTree(artifact.file)) {
                        include("com/mineplex/**")
                        exclude("META-INF/**")
                    }
                }
            }

        // Include Jackson modules from SDK dependencies (they're transitive dependencies of the SDK)
        // This includes all Jackson artifacts that are transitive dependencies
        // EXCEPT jackson-datatype-jsr310 (JavaTimeModule) - we use the server's version to avoid version incompatibility
        project.configurations.compileClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filter {
                val group = it.moduleVersion.id.group
                val name = it.moduleVersion.id.name
                // Include all Jackson datatype, module, and dataformat artifacts
                // BUT exclude jsr310 (JavaTimeModule) - use server's version instead
                ((group == "com.fasterxml.jackson.datatype") && name != "jackson-datatype-jsr310") ||
                        (group == "com.fasterxml.jackson.module") ||
                        (group == "com.fasterxml.jackson.dataformat" && name == "jackson-dataformat-yaml")
            }
            .forEach { artifact ->
                if (artifact.file.exists() && artifact.file.extension == "jar") {
                    from(project.zipTree(artifact.file)) {
                        include("com/fasterxml/jackson/**")
                        exclude("META-INF/**")
                    }
                }
            }

        mergeServiceFiles()
        exclude("org/bukkit/**")
        exclude("org/spigotmc/**")
        exclude("io/papermc/**")

        // Relocate Jackson YAML to avoid conflicts (SDK already includes core Jackson)
        relocate(
            "com.fasterxml.jackson.dataformat.yaml",
            "net.plexverse.enginebridge.relocated.jackson.dataformat.yaml"
        )
    }
}