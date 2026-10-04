package io.sagittarius.panamakvm.platform;

import java.util.Optional;

/**
 * Java service-provider interface for host operating-system integrations.
 * A future Windows module can implement this interface without changing the
 * KVM or core modules.
 */
public interface HostOperatingSystemProvider {
    /**
     * Attempts to create a service for the supplied host.
     *
     * @param platform detected host
     * @return service when this provider supports the host
     */
    Optional<? extends HostOperatingSystem> create(HostPlatform platform);
}
