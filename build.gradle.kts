import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`
    `maven-publish`
    id("com.mineplex.sdk.plugin") version "1.21.9"
    id("com.gradleup.shadow") version "9.1.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // MongoDB driver for DataStorageModule
    implementation(libs.mongodb.driver.sync)
    // Caffeine cache for key field caching
    implementation(libs.caffeine)
    // Kafka clients for MessagingModule
    implementation(libs.kafka.clients)
    // Jackson YAML for world data points parsing
    implementation(libs.jackson.yaml)
}

group = "net.plexverse.enginebridge"
version = "1.0.0"
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
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            
            pom {
                name.set("Plexverse Engine Bridge")
                description.set("Bridge plugin for Plexverse Engine")
                url.set("https://github.com/Plexverse/engine-bridge")
                
                developers {
                    developer {
                        id.set("Plexverse")
                        name.set("Plexverse")
                        organization.set("Plexverse")
                        organizationUrl.set("https://github.com/Plexverse")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/Plexverse/engine-bridge.git")
                    developerConnection.set("scm:git:ssh://github.com/Plexverse/engine-bridge.git")
                    url.set("https://github.com/Plexverse/engine-bridge")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Plexverse/engine-bridge")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

