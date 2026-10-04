package io.sagittarius.panamakvm.platform;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized host operating system and CPU architecture.
 *
 * @param operatingSystem operating-system family
 * @param architecture CPU architecture
 * @param osName original {@code os.name} value
 * @param osArch original {@code os.arch} value
 */
public record HostPlatform(
        OperatingSystem operatingSystem,
        CpuArchitecture architecture,
        String osName,
        String osArch
) {
    /** Validates a host descriptor. */
    public HostPlatform {
        Objects.requireNonNull(operatingSystem, "operatingSystem");
        Objects.requireNonNull(architecture, "architecture");
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(osArch, "osArch");
    }

    /**
     * Detects the current host from standard JVM properties.
     *
     * @return normalized current platform
     */
    public static HostPlatform current() {
        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        String normalizedArch = osArch.toLowerCase(Locale.ROOT);

        OperatingSystem operatingSystem = switch (normalizedOs) {
            case String os when os.contains("linux") -> OperatingSystem.LINUX;
            case String os when os.contains("windows") -> OperatingSystem.WINDOWS;
            case String os when os.contains("mac") || os.contains("darwin") -> OperatingSystem.MACOS;
            default -> OperatingSystem.OTHER;
        };

        CpuArchitecture architecture = switch (normalizedArch) {
            case "amd64", "x86_64" -> CpuArchitecture.X86_64;
            case "aarch64", "arm64" -> CpuArchitecture.AARCH64;
            default -> CpuArchitecture.OTHER;
        };

        return new HostPlatform(operatingSystem, architecture, osName, osArch);
    }

    /**
     * Returns a stable display form suitable for diagnostics.
     *
     * @return normalized operating-system/architecture pair
     */
    public String displayName() {
        return operatingSystem.name().toLowerCase(Locale.ROOT) + "/"
                + architecture.name().toLowerCase(Locale.ROOT);
    }
}
