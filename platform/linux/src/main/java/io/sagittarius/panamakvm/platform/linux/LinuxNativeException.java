package io.sagittarius.panamakvm.platform.linux;

import java.io.Serial;

/** Exception raised when a Linux libc operation returns an errno failure. */
public final class LinuxNativeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** POSIX error number captured immediately after the failed native call. */
    private final int errno;

    /**
     * Creates a native-call failure.
     *
     * @param operation failed operation
     * @param errno captured POSIX errno
     * @param message libc error text
     */
    LinuxNativeException(String operation, int errno, String message) {
        super(operation + " failed: errno=" + errno + " (" + message + ")");
        this.errno = errno;
    }

    /**
     * Returns the captured POSIX errno.
     *
     * @return errno value
     */
    public int errno() {
        return errno;
    }
}
