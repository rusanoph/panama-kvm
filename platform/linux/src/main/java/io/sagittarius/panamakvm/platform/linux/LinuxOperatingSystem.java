package io.sagittarius.panamakvm.platform.linux;

import io.sagittarius.panamakvm.platform.HostOperatingSystem;
import io.sagittarius.panamakvm.platform.HostPlatform;
import io.sagittarius.panamakvm.platform.OperatingSystem;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Linux system-call service implemented directly with the Java FFM API.
 * It deliberately exposes Linux primitives only; portable code depends on
 * {@link HostOperatingSystem} instead.
 */
public final class LinuxOperatingSystem implements HostOperatingSystem {
    /** Return value used internally to report that an ioctl was interrupted. */
    public static final int INTERRUPTED = Integer.MIN_VALUE;
    /** POSIX errno value for an interrupted system call. */
    public static final int EINTR = 4;

    private final HostPlatform platform;
    private final LinuxNative nativeCalls;

    /**
     * Creates a Linux service for the current host.
     */
    public LinuxOperatingSystem() {
        this(HostPlatform.current());
    }

    /**
     * Creates a Linux service for an already detected host.
     *
     * @param platform host descriptor
     */
    public LinuxOperatingSystem(HostPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
        if (platform.operatingSystem() != OperatingSystem.LINUX) {
            throw new UnsupportedOperationException("Linux service cannot run on " + platform.displayName());
        }
        this.nativeCalls = new LinuxNative();
    }

    @Override
    public HostPlatform platform() {
        return platform;
    }

    /**
     * Opens a path for read/write access.
     *
     * @param path filesystem path
     * @param arena arena retaining the encoded native path during the call
     * @return opened descriptor
     */
    public NativeFileDescriptor openReadWrite(Path path, Arena arena) {
        return new NativeFileDescriptor(nativeCalls.openReadWrite(path, arena));
    }

    /**
     * Issues an ioctl whose request has no third argument.
     *
     * @param descriptor target descriptor
     * @param request Linux ioctl request code
     * @return ioctl return value
     */
    public int ioctl(NativeFileDescriptor descriptor, long request) {
        return nativeCalls.ioctlNoArgument(descriptor.value(), request, false);
    }

    /**
     * Issues a restartable ioctl. An {@link #INTERRUPTED} result means the
     * native call returned {@link #EINTR}; all other failures throw.
     *
     * @param descriptor target descriptor
     * @param request Linux ioctl request code
     * @return ioctl result or {@link #INTERRUPTED}
     */
    public int ioctlRestartable(NativeFileDescriptor descriptor, long request) {
        return nativeCalls.ioctlNoArgument(descriptor.value(), request, true);
    }

    /**
     * Issues an ioctl with an unsigned-long-compatible scalar argument.
     *
     * @param descriptor target descriptor
     * @param request Linux ioctl request code
     * @param argument scalar argument
     * @return ioctl return value
     */
    public int ioctl(NativeFileDescriptor descriptor, long request, long argument) {
        return nativeCalls.ioctlLong(descriptor.value(), request, argument);
    }

    /**
     * Issues an ioctl with a pointer argument.
     *
     * @param descriptor target descriptor
     * @param request Linux ioctl request code
     * @param argument native argument buffer
     * @return ioctl return value
     */
    public int ioctl(NativeFileDescriptor descriptor, long request, MemorySegment argument) {
        return nativeCalls.ioctlAddress(descriptor.value(), request, argument);
    }

    /**
     * Maps descriptor-backed memory as shared read/write memory.
     *
     * @param descriptor target descriptor
     * @param length mapping length
     * @param arena arena controlling mapping lifetime
     * @return mapped segment
     */
    public MemorySegment mapShared(NativeFileDescriptor descriptor, long length, Arena arena) {
        return nativeCalls.mapShared(descriptor.value(), length, arena);
    }

    /**
     * Closes a descriptor, preserving any close error.
     *
     * @param descriptor descriptor to close
     */
    public void close(NativeFileDescriptor descriptor) {
        nativeCalls.close(descriptor.value(), false);
    }

    /**
     * Closes a descriptor without masking an already propagating failure.
     *
     * @param descriptor nullable descriptor
     */
    public void closeQuietly(NativeFileDescriptor descriptor) {
        if (descriptor != null) {
            nativeCalls.close(descriptor.value(), true);
        }
    }
}
