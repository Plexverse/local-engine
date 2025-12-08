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

- MineplexGameModule
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

## Building

Build the plugin using Gradle:

```bash
./gradlew build
```

The JAR will be in `build/libs/`.

## Usage

### Docker Setup

For a complete local development environment with Docker Compose, see [local-docker](https://github.com/Plexverse/local-docker). This repository provides:
- A Docker image for locally running the game server
- Docker Compose setup guide
- Instructions for running everything from a project folder to a functional server
- Required services (MongoDB, Kafka, etc.) configured and ready to use
- Automatically downloads the latest local-engine release

> [!IMPORTANT]
> This plugin is **only intended for local running**. Do not use this in production environments. For production, use the official Studio Engine.

## Contributing

> [!NOTE]
> Most Studio Engine modules are not yet supported. If you'd like to contribute support for additional modules, please use open source tooling and submit a pull request. Contributions are welcome and appreciated!

