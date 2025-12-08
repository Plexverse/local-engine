# Plexverse Engine Bridge

A bridge plugin for Mineplex's Studio Engine that allows for local running. This plugin implements Mineplex's Studio Engine's main modules but enables local development and testing without requiring the full Studio Engine infrastructure.

## Overview

Plexverse Engine Bridge provides a local implementation of Mineplex's Studio Engine core functionality, making it possible to run and develop Mineplex Games plugins locally without the full Studio Engine setup. It maintains compatibility with Studio Engine's main modules while providing a simplified local environment.

> [!NOTE]
> Most Studio Engine modules are not yet supported. Contributions are welcome to add support for additional modules using open source tooling. See the list below for a list of implemented modules.


## Features

- ChatModule
- WorldModule 
- LobbyModule
- DataStoreModule - using mongo connector
- StatsModule - using DataStoreModule
- LevelModule - using DataStoreModule with basic fake level algorithm
- MessagingModule - using kafka connector
- MatchmakerModule - (empty implementation - no proxy support yet, add your own!)

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

## Contributing

> [!NOTE]
> Most Studio Engine modules are not yet supported. If you'd like to contribute support for additional modules, please use open source tooling and submit a pull request. Contributions are welcome and appreciated!

