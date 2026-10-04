package io.sagittarius.panamakvm.virtualization.linux;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Inputs for direct Linux kernel boot, independent of firmware and disk format.
 *
 * @param kernel x86 Linux bzImage path
 * @param initrd optional initramfs path
 * @param commandLine Linux kernel command line
 */
public record LinuxBootSpec(Path kernel, Optional<Path> initrd, String commandLine) {
    /** Validates and normalizes boot inputs. */
    public LinuxBootSpec {
        kernel = Objects.requireNonNull(kernel, "kernel").toAbsolutePath().normalize();
        initrd = Objects.requireNonNull(initrd, "initrd")
                .map(path -> path.toAbsolutePath().normalize());
        Objects.requireNonNull(commandLine, "commandLine");
        if (commandLine.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Kernel command line must not contain NUL");
        }
    }

    /**
     * Creates a boot specification without an initramfs.
     *
     * @param kernel bzImage path
     * @param commandLine kernel command line
     * @return boot specification
     */
    public static LinuxBootSpec kernelOnly(Path kernel, String commandLine) {
        return new LinuxBootSpec(kernel, Optional.empty(), commandLine);
    }
}
