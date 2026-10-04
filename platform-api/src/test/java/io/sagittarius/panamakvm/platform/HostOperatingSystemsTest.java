package io.sagittarius.panamakvm.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HostOperatingSystemsTest {
    @Test
    void reportsWhenNoInstalledProviderSupportsHost() {
        HostPlatform platform = new HostPlatform(OperatingSystem.LINUX, CpuArchitecture.X86_64,
                "Linux", "x86_64");

        assertThrows(UnsupportedOperationException.class,
                () -> HostOperatingSystems.forPlatform(platform));
    }
}
