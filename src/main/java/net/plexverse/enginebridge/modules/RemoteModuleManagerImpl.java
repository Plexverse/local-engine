package net.plexverse.enginebridge.modules;

import com.mineplex.studio.sdk.modules.MineplexModule;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Remote implementation of ModuleManager that delegates to MineplexModuleManager.
 * Used when Studio Engine is present and we want to use the remote implementation.
 */
@RequiredArgsConstructor
public class RemoteModuleManagerImpl implements ModuleManager {

    private final MineplexModuleManager delegate;

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getRegisteredModule(@NotNull final Class<T> moduleClass) {
        if (MineplexModule.class.isAssignableFrom(moduleClass)) {
            return (T) MineplexModuleManager.getRegisteredModule((Class<? extends MineplexModule>) moduleClass);
        }
        return null;
    }

    @Override
    @NotNull
    public <T> ModuleManager registerModule(@NotNull final T instance) {
        if (instance instanceof MineplexModule) {
            delegate.registerModule((MineplexModule) instance);
        } else {
            throw new IllegalArgumentException("Instance must be a MineplexModule");
        }
        return this;
    }

    @Override
    public void destroyModule(@NotNull final Class<?> module) {
        if (MineplexModule.class.isAssignableFrom(module)) {
            @SuppressWarnings("unchecked")
            final Class<? extends MineplexModule> mineplexModuleClass = (Class<? extends MineplexModule>) module;
            delegate.destroyModule(mineplexModuleClass);
        } else {
            throw new IllegalArgumentException("Module class must extend MineplexModule");
        }
    }
}

