package io.sagittarius.panamakvm.virtualization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/** Discovers installed {@link VirtualizationProvider} implementations. */
public final class VirtualizationProviders {
    private VirtualizationProviders() { }

    /**
     * Loads providers visible to the current thread context class loader.
     *
     * @return immutable provider map keyed by stable ID
     */
    public static Map<String, VirtualizationProvider> discover() {
        Map<String, VirtualizationProvider> providers = new LinkedHashMap<>();
        ServiceLoader.load(VirtualizationProvider.class).forEach(provider -> {
            VirtualizationProvider previous = providers.putIfAbsent(provider.id(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate virtualization backend ID: " + provider.id());
            }
        });
        return Map.copyOf(providers);
    }
}
