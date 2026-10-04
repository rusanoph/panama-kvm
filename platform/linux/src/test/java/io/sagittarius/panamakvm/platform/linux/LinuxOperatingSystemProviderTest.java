package io.sagittarius.panamakvm.platform.linux;

import io.sagittarius.panamakvm.platform.HostOperatingSystems;
import io.sagittarius.panamakvm.platform.HostOperatingSystemProviders;
import io.sagittarius.panamakvm.platform.HostPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class LinuxOperatingSystemProviderTest {
    @Test
    void isDiscoveredAsInstalledProvider() {
        assertTrue(HostOperatingSystemProviders.discover().stream()
                .anyMatch(provider -> provider.create(HostPlatform.current()).isPresent()));
    }

    @Test
    void isDiscoveredThroughHostOperatingSystemSpi() {
        assertInstanceOf(LinuxOperatingSystem.class, HostOperatingSystems.current());
    }
}
