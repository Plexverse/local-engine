<img width="4096" height="843" alt="Github Repository Header" src="https://github.com/user-attachments/assets/7e2b04c5-a422-49c5-9d24-4bd1635a0627" />
</br>
</br>

![Version](https://img.shields.io/badge/version-1.0.0-blue?style=flat-square)

A bridge plugin for Mineplex's Studio Engine that allows for local running. This plugin implements Mineplex's Studio Engine's main modules but enables local development and testing without requiring the full Studio Engine infrastructure.

## Overview

Plexverse Engine Bridge provides a local implementation of Mineplex's Studio Engine core functionality, making it possible to run and develop Mineplex Games plugins locally without the full Studio Engine setup. It maintains compatibility with Studio Engine's main modules while providing a simplified local environment.

> [!NOTE]
> Most Studio Engine modules are not yet supported. Contributions are welcome to add support for additional modules using open source tooling. See the list below for a list of implemented modules.


## Features

For detailed information about the built-in modules and their APIs, see the [Mineplex Studio SDK Features documentation](https://docs.mineplex.com/docs/sdk/features).

The module signatures match the official Studio Engine SDK, but the implementations differ for local running. See the linked readmes for each feature below for requirements and setup instructions for local use.

- [ResourcePackModule](src/main/java/net/plexverse/enginebridge/modules/resourcepack/README.md) - Local HTTP server for serving resource packs
- [ChatModule](src/main/java/net/plexverse/enginebridge/modules/chat/README.md) - Chat channel management, filtering, and rendering
- [WorldModule](src/main/java/net/plexverse/enginebridge/modules/world/README.md) - World creation and management with data points support
- [LobbyModule](src/main/java/net/plexverse/enginebridge/modules/lobby/README.md) - Lobby management with spawn protection and game state handling
- [DataStoreModule](src/main/java/net/plexverse/enginebridge/modules/datastore/README.md) - MongoDB-based data storage for structured and binary data
- [StatsModule](src/main/java/net/plexverse/enginebridge/modules/stats/README.md) - Player statistics management using DataStoreModule
- LevelModule - using DataStoreModule with basic fake level algorithm
- [MessagingModule](src/main/java/net/plexverse/enginebridge/modules/messaging/README.md) - Kafka-based inter-server messaging
- QueuingModule - Local in-memory queue for matchmaking (no proxy support yet)

## Requirements

- Java 21+
- Paper/Spigot 1.21+
- StudioEngine (optional - soft dependency - this plugin will act as a proxy if present)

## Building

Build the plugin using Gradle:

```bash
./gradlew build
```

The JAR will be in `build/libs/`.

## Usage

Place the plugin JAR in your server's `plugins` folder. The plugin will provide Studio Engine functionality for local development and testing.

## Migration Guide

To migrate from using Mineplex's Studio Engine to Plexverse Engine Bridge for local development:

### 1. Add Engine Bridge as a Dependency

Add `engine-bridge` as a `compileOnly` dependency in your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Plexverse/engine-bridge")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("net.plexverse.enginebridge:engine-bridge:VERSION")
}
```

**Note:** 
- Replace `VERSION` with the latest release version (e.g., `1.0.0`)
- **Authentication is required** even for public packages. GitHub Packages requires authentication for all Maven packages, regardless of repository visibility
- Create a Personal Access Token (PAT) with the `read:packages` permission
- Set it as `GITHUB_TOKEN` environment variable or `gpr.token` Gradle property

### 2. Replace StudioEngine in Plugin Dependencies

In your `plugin.yml`, replace `StudioEngine` with `PlexverseEngineBridge` in the `depend` or `softdepend` section:

**Before:**
```yaml
depend: [ StudioEngine ]
# or
softdepend: [ StudioEngine ]
```

**After:**
```yaml
depend: [ PlexverseEngineBridge ]
# or
softdepend: [ PlexverseEngineBridge ]
```

**Note:** If you're using `softdepend`, the Engine Bridge will work as a fallback if Studio Engine is not present. If you're using `depend`, your plugin will require Engine Bridge to be loaded.

### 3. Replace ModuleManager Imports

Replace all imports of `MineplexModuleManager` with `ModuleManager` from Engine Bridge:

**Before:**
```java
import com.mineplex.studio.sdk.modules.MineplexModuleManager;

MineplexModuleManager.getRegisteredModule(ChatModule.class);
MineplexModuleManager.getInstance().registerModule(myModule);
```

**After:**
```java
import net.plexverse.enginebridge.modules.ModuleManager;

ModuleManager.getRegisteredModule(ChatModule.class);
ModuleManager.getInstance().registerModule(myModule);
```

The API is identical, so no other code changes are needed. The Engine Bridge will automatically use the remote MineplexModuleManager if Studio Engine is present, or fall back to the local implementation otherwise.

## Contributing

> [!NOTE]
> Most Studio Engine modules are not yet supported. If you'd like to contribute support for additional modules, please use open source tooling and submit a pull request. Contributions are welcome and appreciated!

