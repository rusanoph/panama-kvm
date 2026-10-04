package io.sagittarius.panamakvm.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostPlatformTest {
    @Test
    void formatsNormalizedPlatform() {
        HostPlatform platform = new HostPlatform(OperatingSystem.WINDOWS, CpuArchitecture.X86_64,
                "Windows 11", "amd64");
        assertEquals("windows/x86_64", platform.displayName());
    }
}
