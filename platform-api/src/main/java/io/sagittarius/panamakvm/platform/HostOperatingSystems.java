package io.sagittarius.panamakvm.platform;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves the host operating-system service supplied by installed modules. */
public final class HostOperatingSystems {
    private HostOperatingSystems() { }

    /**
     * Resolves the service for the JVM's current host platform.
     *
     * @return the only service supporting the current host
     */
    public static HostOperatingSystem current() {
        return forPlatform(HostPlatform.current());
    }

    /**
     * Resolves the only installed service that supports {@code platform}.
     *
     * @param platform host descriptor
     * @return host operating-system service
     * @throws UnsupportedOperationException when no installed provider supports the host
     * @throws IllegalStateException when more than one provider supports the host
     */
    public static HostOperatingSystem forPlatform(HostPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        List<HostOperatingSystem> services = HostOperatingSystemProviders.discover()
                .stream()
                .map(provider -> provider.create(platform))
                .flatMap(Optional::stream)
                .map(HostOperatingSystem.class::cast)
                .toList();

        if (services.isEmpty()) {
            throw new UnsupportedOperationException(
                    "No host operating-system provider supports " + platform.displayName());
        }
        if (services.size() > 1) {
            throw new IllegalStateException(
                    "Multiple host operating-system providers support " + platform.displayName());
        }
        return services.getFirst();
    }
}
