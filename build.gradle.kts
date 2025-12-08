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
    implementation(libs.bundles.reflections)
    implementation(libs.bundles.apache)
    implementation(libs.bundles.scoreboard)
    implementation(libs.mapstruct)
    implementation(libs.adventure.platform.api)
    implementation(libs.gson)
    annotationProcessor(libs.mapstruct.processor)
    implementation("commons-codec:commons-codec:1.17.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // OTLP/gRPC dependencies
    implementation("io.opentelemetry:opentelemetry-api:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk-logs:1.32.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.32.0")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.21.0-alpha")
    implementation("io.grpc:grpc-netty-shaded:1.60.0")
    implementation("io.grpc:grpc-protobuf:1.60.0")
    implementation("io.grpc:grpc-stub:1.60.0")
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
                
                licenses {
                    license {
                        name.set("Proprietary")
                    }
                }
                
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

