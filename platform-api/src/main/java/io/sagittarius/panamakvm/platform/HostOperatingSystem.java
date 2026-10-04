package io.sagittarius.panamakvm.platform;

/**
 * Service boundary for host-specific facilities. Platform implementations may
 * expose narrower extension interfaces (for example POSIX file descriptors)
 * without forcing unrelated Windows implementations into a Unix-shaped API.
 */
public interface HostOperatingSystem {
    /**
     * Returns the host described by this service.
     *
     * @return host platform
     */
    HostPlatform platform();
}
