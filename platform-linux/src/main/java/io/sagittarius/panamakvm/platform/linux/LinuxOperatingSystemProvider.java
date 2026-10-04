package io.sagittarius.panamakvm.platform.linux;

import com.google.auto.service.AutoService;
import io.sagittarius.panamakvm.platform.HostOperatingSystem;
import io.sagittarius.panamakvm.platform.HostOperatingSystemProvider;
import io.sagittarius.panamakvm.platform.HostPlatform;
import io.sagittarius.panamakvm.platform.OperatingSystem;

import java.util.Optional;

/** Java service provider for the FFM-based Linux operating-system layer. */
@AutoService(HostOperatingSystemProvider.class)
public final class LinuxOperatingSystemProvider implements HostOperatingSystemProvider {
    /** Creates the stateless Linux host-service provider. */
    public LinuxOperatingSystemProvider() { }

    @Override
    public Optional<? extends HostOperatingSystem> create(HostPlatform platform) {
        return platform.operatingSystem() == OperatingSystem.LINUX
                ? Optional.of(new LinuxOperatingSystem(platform))
                : Optional.empty();
    }
}
