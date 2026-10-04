package io.sagittarius.panamakvm.platform.linux;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Package-private, type-safe libc binding used by {@link LinuxOperatingSystem}. */
final class LinuxNative {
    /** Linux {@code O_RDWR}: open for both reading and writing. */
    private static final int OPEN_READ_WRITE = 0x0002;
    /** Linux {@code PROT_READ}: mapped pages may be read. */
    private static final int PROTECTION_READ = 0x1;
    /** Linux {@code PROT_WRITE}: mapped pages may be written. */
    private static final int PROTECTION_WRITE = 0x2;
    /** Linux {@code MAP_SHARED}: changes are shared with the mapped object. */
    private static final int MAPPING_SHARED = 0x01;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBC = LINKER.defaultLookup();
    private static final Linker.Option CAPTURE_ERRNO = Linker.Option.captureCallState("errno");
    private static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
    private static final VarHandle ERRNO = CAPTURE_LAYOUT.varHandle(groupElement("errno"));

    private static final MethodHandle OPEN = downcall("open",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT),
            CAPTURE_ERRNO, Linker.Option.firstVariadicArg(2));
    private static final MethodHandle CLOSE = downcall("close",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT), CAPTURE_ERRNO);
    private static final MethodHandle IOCTL_NO_ARGUMENT = downcall("ioctl",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
            CAPTURE_ERRNO, Linker.Option.firstVariadicArg(2));
    private static final MethodHandle IOCTL_LONG_ARGUMENT = downcall("ioctl",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG),
            CAPTURE_ERRNO, Linker.Option.firstVariadicArg(2));
    private static final MethodHandle IOCTL_ADDRESS_ARGUMENT = downcall("ioctl",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS),
            CAPTURE_ERRNO, Linker.Option.firstVariadicArg(2));
    private static final MethodHandle MMAP = downcall("mmap",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG),
            CAPTURE_ERRNO);
    private static final MethodHandle MUNMAP = downcall("munmap",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG), CAPTURE_ERRNO);
    private static final MethodHandle STRERROR = downcall("strerror",
            FunctionDescriptor.of(ADDRESS, JAVA_INT));

    int openReadWrite(Path path, Arena arena) {
        MemorySegment nativePath = arena.allocateFrom(path.toString());
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            int descriptor = (int) OPEN.invokeExact(state, nativePath, OPEN_READ_WRITE);
            if (descriptor < 0) {
                throw failure("open(" + path + ")", errno(state));
            }
            return descriptor;
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FFM open invocation failed", throwable);
        }
    }

    int ioctlNoArgument(int descriptor, long request, boolean allowInterrupt) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            // Materialize a zero third argument. Leaving the variadic register unspecified
            // can pass stale native state; KVM rejects non-zero args for _IO requests.
            int result = (int) IOCTL_NO_ARGUMENT.invokeExact(state, descriptor, request, 0L);
            if (result < 0) {
                int error = errno(state);
                if (allowInterrupt && error == LinuxOperatingSystem.EINTR) {
                    return LinuxOperatingSystem.INTERRUPTED;
                }
                throw failure(ioctlName(request), error);
            }
            return result;
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FFM ioctl invocation failed", throwable);
        }
    }

    int ioctlLong(int descriptor, long request, long argument) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            int result = (int) IOCTL_LONG_ARGUMENT.invokeExact(state, descriptor, request, argument);
            if (result < 0) {
                throw failure(ioctlName(request), errno(state));
            }
            return result;
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FFM ioctl invocation failed", throwable);
        }
    }

    int ioctlAddress(int descriptor, long request, MemorySegment argument) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            int result = (int) IOCTL_ADDRESS_ARGUMENT.invokeExact(state, descriptor, request, argument);
            if (result < 0) {
                throw failure(ioctlName(request), errno(state));
            }
            return result;
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FFM ioctl invocation failed", throwable);
        }
    }

    MemorySegment mapShared(int descriptor, long length, Arena arena) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            MemorySegment address = (MemorySegment) MMAP.invokeExact(state, MemorySegment.NULL, length,
                    PROTECTION_READ | PROTECTION_WRITE, MAPPING_SHARED, descriptor, 0L);
            if (address.address() == -1L) {
                throw failure("mmap", errno(state));
            }
            return address.reinterpret(length, arena, ignored -> unmap(address, length));
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FFM mmap invocation failed", throwable);
        }
    }

    void close(int descriptor, boolean quiet) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            int result = (int) CLOSE.invokeExact(state, descriptor);
            if (result < 0 && !quiet) {
                throw failure("close", errno(state));
            }
        } catch (RuntimeException | Error exception) {
            if (!quiet) {
                throw exception;
            }
        } catch (Throwable throwable) {
            if (!quiet) {
                throw new IllegalStateException("FFM close invocation failed", throwable);
            }
        }
    }

    private void unmap(MemorySegment address, long length) {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment state = call.allocate(CAPTURE_LAYOUT);
            int result = (int) MUNMAP.invokeExact(state, address, length);
            if (result < 0) {
                throw failure("munmap", errno(state));
            }
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FFM munmap invocation failed", throwable);
        }
    }

    private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor, Linker.Option... options) {
        return LINKER.downcallHandle(LIBC.find(symbol).orElseThrow(
                () -> new IllegalStateException("libc symbol not found: " + symbol)), descriptor, options);
    }

    private static int errno(MemorySegment state) {
        return (int) ERRNO.get(state, 0L);
    }

    private static String ioctlName(long request) {
        return "ioctl(0x" + Long.toHexString(request) + ')';
    }

    private static LinuxNativeException failure(String operation, int error) {
        return new LinuxNativeException(operation, error, errorText(error));
    }

    private static String errorText(int error) {
        try {
            MemorySegment address = (MemorySegment) STRERROR.invokeExact(error);
            return address.reinterpret(1024).getString(0);
        } catch (Throwable ignored) {
            return "unknown native error";
        }
    }
}
