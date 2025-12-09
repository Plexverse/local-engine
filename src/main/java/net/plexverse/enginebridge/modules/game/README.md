# MineplexGameMechanicFactory

Local implementation of Mineplex's GameMechanicFactory for Plexverse Engine Bridge.

## Overview

The MineplexGameMechanicFactory provides a mechanism for constructing and managing game mechanics. It implements the same API as the official Mineplex Studio Engine GameMechanicFactory, allowing seamless migration between local and production environments. The local implementation provides a simplified factory that supports mechanic registration and construction with fallback to default constructors.

## Features

- **Mechanic Registration**: Register custom factory suppliers for game mechanics
- **Priority-Based Registration**: Support for multiple providers with priority ordering
- **Mechanic Construction**: Construct mechanics using registered providers or default constructors
- **Provider Management**: Check if a mechanic type has a registered provider
- **Full API Compatibility**: Matches the official MineplexGameMechanicFactory interface exactly

## Local Implementation Details

### Default Constructor Fallback

The local implementation attempts to use a default constructor if no provider is registered for a mechanic type. This allows mechanics with default constructors to work out of the box for local development. If a mechanic requires constructor parameters, it must be registered with a factory supplier.

### Provider Priority

When multiple providers are registered for the same mechanic type, the factory uses the provider with the highest priority (as determined by `ServicePriority`). This allows overriding default implementations with custom ones.

### Registration

Mechanics can be registered using:
- `register(Class<M>, Supplier<M>)` - Register with default priority
- `register(Class<M>, Supplier<M>, ServicePriority)` - Register with specific priority

## Usage

### Basic Usage

```java
MineplexGameMechanicFactory factory = MineplexModuleManager.getRegisteredModule(MineplexGameMechanicFactory.class);

// Register a mechanic with a factory
factory.register(MyMechanic.class, () -> new MyMechanic());

// Construct a mechanic
MyMechanic mechanic = factory.construct(MyMechanic.class);

// Check if a mechanic type is registered
if (factory.contains(MyMechanic.class)) {
    // Mechanic is available
}
```

### Registering with Priority

```java
factory.register(
    MyMechanic.class,
    () -> new CustomMyMechanic(),
    ServicePriority.Highest
);
```

### Using with Game Setup

The factory is automatically registered and available during game setup:

```java
MineplexGame game = ...;
MineplexGameMechanicFactory factory = game.getGameMechanicFactory();

// Construct mechanics for the game
LegacyMechanic legacy = factory.construct(LegacyMechanic.class);
legacy.setup(game);
```

## Differences from Official Implementation

### Provider Registration

The official Studio Engine implementation pre-registers many built-in mechanics (LegacyMechanic, PlayerAFKMechanic, etc.) during setup. The local implementation does not pre-register these mechanics, so they must either:
- Have a default constructor (for automatic fallback)
- Be registered manually by your game code

### Error Handling

If a mechanic cannot be constructed (no provider and no default constructor), the local implementation throws an `IllegalArgumentException` with details about the failure. This helps identify which mechanics need to be registered.

## API Compatibility

The local implementation fully implements the `MineplexGameMechanicFactory` interface from the Mineplex Studio SDK:

- ✅ `construct(Class<M>)` - Construct a mechanic instance
- ✅ `register(Class<M>, Supplier<M>)` - Register a factory supplier
- ✅ `register(Class<M>, Supplier<M>, ServicePriority)` - Register with priority
- ✅ `contains(Class<? extends GameMechanic<?>>)` - Check if a mechanic is registered
- ✅ `setup()` - Initialize the factory
- ✅ `teardown()` - Clean up resources

## Troubleshooting

### "No provider for X and default constructor failed"

This error occurs when:
1. No provider is registered for the mechanic type
2. The mechanic class doesn't have a default (no-args) constructor

**Solution**: Register a factory supplier for the mechanic:
```java
factory.register(MyMechanic.class, () -> new MyMechanic(arg1, arg2));
```

### Mechanics Not Working

If mechanics aren't working as expected:
1. Check that the factory is registered: `MineplexModuleManager.getRegisteredModule(MineplexGameMechanicFactory.class)`
2. Verify the mechanic is registered: `factory.contains(MyMechanic.class)`
3. Check the logs for registration messages

## Links

- [Official Mineplex Studio SDK Documentation](https://docs.mineplex.com/docs/sdk/features)
- [Main README](../../../../README.md)

