package io.sagittarius.panamakvm.platform;

import java.util.List;
import java.util.ServiceLoader;

/** Discovers installed {@link HostOperatingSystemProvider} implementations. */
public final class HostOperatingSystemProviders {
    private HostOperatingSystemProviders() { }

    /**
     * Loads providers visible to the current thread context class loader.
     *
     * @return immutable list of installed providers
     */
    public static List<HostOperatingSystemProvider> discover() {
        return ServiceLoader.load(HostOperatingSystemProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }
}
