package io.sagittarius.panamakvm.platform.linux;

/**
 * A non-owning Linux file-descriptor value. Ownership remains with the
 * {@link LinuxOperatingSystem} call site that opened it.
 *
 * @param value non-negative descriptor number
 */
public record NativeFileDescriptor(int value) {
    /** Validates a file descriptor. */
    public NativeFileDescriptor {
        if (value < 0) {
            throw new IllegalArgumentException("File descriptor must be non-negative: " + value);
        }
    }
}
