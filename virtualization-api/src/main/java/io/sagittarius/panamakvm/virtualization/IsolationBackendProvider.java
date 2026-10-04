package io.sagittarius.panamakvm.virtualization;

import io.sagittarius.panamakvm.platform.HostPlatform;

/**
 * Common discovery metadata for isolation backends. KVM implementations use
 * {@link IsolationKind#VIRTUAL_MACHINE}; an OpenVZ module would use
 * {@link IsolationKind#CONTAINER} instead of pretending to expose VM exits.
 */
public interface IsolationBackendProvider {
    /**
     * Returns the stable command-line and service identifier.
     *
     * @return lowercase backend identifier
     */
    String id();

    /**
     * Returns the isolation semantics implemented by this backend.
     *
     * @return isolation kind
     */
    IsolationKind kind();

    /**
     * Probes backend availability without creating a workload.
     *
     * @param host detected host
     * @return probe result
     */
    BackendAvailability availability(HostPlatform host);
}
