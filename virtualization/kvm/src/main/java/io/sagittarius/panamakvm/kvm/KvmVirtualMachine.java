package io.sagittarius.panamakvm.kvm;

import io.sagittarius.panamakvm.core.GuestMemory;
import io.sagittarius.panamakvm.core.VmExit;
import io.sagittarius.panamakvm.kvm.boot.LinuxBootLayout;
import io.sagittarius.panamakvm.kvm.boot.LinuxBootLoader;
import io.sagittarius.panamakvm.kvm.boot.LinuxKernelImage;
import io.sagittarius.panamakvm.platform.CpuArchitecture;
import io.sagittarius.panamakvm.platform.HostOperatingSystem;
import io.sagittarius.panamakvm.platform.HostOperatingSystems;
import io.sagittarius.panamakvm.platform.HostPlatform;
import io.sagittarius.panamakvm.platform.OperatingSystem;
import io.sagittarius.panamakvm.platform.linux.LinuxOperatingSystem;
import io.sagittarius.panamakvm.platform.linux.NativeFileDescriptor;
import io.sagittarius.panamakvm.virtualization.VirtualMachineConfiguration;
import io.sagittarius.panamakvm.virtualization.linux.LinuxBootSpec;
import io.sagittarius.panamakvm.virtualization.linux.LinuxBootableMachine;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.util.Objects;

import static io.sagittarius.panamakvm.kvm.KvmLayouts.CPUID_ENTRY_SIZE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.CPUID_HEADER_SIZE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.CPUID_MAX_ENTRIES;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.DESCRIPTOR_TABLE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.DESCRIPTOR_TABLE_BASE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.DESCRIPTOR_TABLE_LIMIT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.IRQ_LEVEL_SIZE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.PIT_CONFIGURATION_SIZE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS_RBP;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS_RBX;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS_RDI;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS_RFLAGS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS_RIP;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.REGISTERS_RSI;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_EXIT_REASON;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_IO_COUNT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_IO_DATA_OFFSET;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_IO_DIRECTION;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_IO_PORT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_IO_SIZE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_INTERRUPT_FLAG;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_MMIO_DATA;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_MMIO_IS_WRITE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_MMIO_LENGTH;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_MMIO_PHYSICAL_ADDRESS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_SYSTEM_EVENT_FLAGS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.RUN_SYSTEM_EVENT_TYPE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_BASE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_DB;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_DPL;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_GRANULARITY;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_LIMIT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_LONG;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_PRESENT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_S;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_SELECTOR;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SEGMENT_TYPE;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_CS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_DS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_EFER;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_ES;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_FS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_GDT;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_GS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_REGISTERS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_SS;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.SPECIAL_CR0;
import static io.sagittarius.panamakvm.kvm.KvmLayouts.USER_MEMORY_REGION;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * One-vCPU Linux x86-64 KVM machine. The class owns every native descriptor,
 * mapping, and arena and releases them in dependency order.
 */
public final class KvmVirtualMachine implements LinuxBootableMachine {
    /** Smallest supported RAM size for modern distribution kernels. */
    public static final long MINIMUM_GUEST_MEMORY = 64L << 20;
    /**
     * Current upper bound, kept below KVM's high x86 MMIO/TSS/APIC reservations
     * while the machine has a single contiguous low-memory slot.
     */
    public static final long MAXIMUM_GUEST_MEMORY = 3L << 30;
    /** Host page size required for the one-slot guest-memory registration. */
    public static final long HOST_PAGE_SIZE = 4096;
    /** Guest-physical address of the bootstrap GDT. */
    public static final long BOOT_GDT_ADDRESS = 0x500;
    /** Linux x86 boot-protocol code-segment selector. */
    public static final int BOOT_CODE_SELECTOR = 0x10;
    /** Linux x86 boot-protocol data-segment selector. */
    public static final int BOOT_DATA_SELECTOR = 0x18;
    /** Flat 32-bit execute/read GDT descriptor. */
    public static final long BOOT_CODE_DESCRIPTOR = 0x00cf_9a00_0000_ffffL;
    /** Flat 32-bit read/write GDT descriptor. */
    public static final long BOOT_DATA_DESCRIPTOR = 0x00cf_9200_0000_ffffL;

    private final LinuxOperatingSystem operatingSystem;
    private final Arena nativeArena;
    private final Arena runArena;
    private final SegmentGuestMemory guestMemory;
    private final MemorySegment runState;
    private NativeFileDescriptor kvmDescriptor;
    private NativeFileDescriptor vmDescriptor;
    private NativeFileDescriptor vcpuDescriptor;
    private boolean booted;
    private boolean running;
    private boolean closed;

    private KvmVirtualMachine(
            LinuxOperatingSystem operatingSystem,
            Arena nativeArena,
            Arena runArena,
            SegmentGuestMemory guestMemory,
            MemorySegment runState,
            NativeFileDescriptor kvmDescriptor,
            NativeFileDescriptor vmDescriptor,
            NativeFileDescriptor vcpuDescriptor
    ) {
        this.operatingSystem = operatingSystem;
        this.nativeArena = nativeArena;
        this.runArena = runArena;
        this.guestMemory = guestMemory;
        this.runState = runState;
        this.kvmDescriptor = kvmDescriptor;
        this.vmDescriptor = vmDescriptor;
        this.vcpuDescriptor = vcpuDescriptor;
    }

    /**
     * Creates native KVM, VM, irqchip, PIT, memory, and bootstrap-vCPU state.
     *
     * @param configuration VM resources
     * @return stopped machine ready for {@link #bootLinux(LinuxBootSpec)}
     */
    public static KvmVirtualMachine create(VirtualMachineConfiguration configuration) {
        HostOperatingSystem operatingSystem = HostOperatingSystems.current();
        if (!(operatingSystem instanceof LinuxOperatingSystem linux)) {
            throw new UnsupportedOperationException("KVM requires a Linux host service; resolved "
                    + operatingSystem.getClass().getName());
        }
        return create(configuration, linux);
    }

    /**
     * Creates native KVM state using the Linux host service selected by the caller.
     *
     * @param configuration VM resources
     * @param os Linux host service
     * @return stopped machine ready for {@link #bootLinux(LinuxBootSpec)}
     */
    static KvmVirtualMachine create(
            VirtualMachineConfiguration configuration,
            LinuxOperatingSystem os
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(os, "os");
        requireSupportedHostAndConfiguration(configuration);

        Arena nativeArena = Arena.ofShared();
        Arena runArena = Arena.ofShared();
        NativeFileDescriptor kvm = null;
        NativeFileDescriptor vm = null;
        NativeFileDescriptor vcpu = null;

        try {
            kvm = os.openReadWrite(KvmVirtualizationProvider.DEVICE, nativeArena);
            int apiVersion = os.ioctl(kvm, KvmUapi.GET_API_VERSION);
            if (apiVersion != KvmUapi.API_VERSION) {
                throw new IllegalStateException("Unsupported KVM API version " + apiVersion
                        + "; expected " + KvmUapi.API_VERSION);
            }
            if (os.ioctl(kvm, KvmUapi.CHECK_EXTENSION, KvmUapi.CAPABILITY_USER_MEMORY) <= 0) {
                throw new IllegalStateException("KVM_CAP_USER_MEMORY is not supported by this host");
            }
            vm = new NativeFileDescriptor(os.ioctl(kvm, KvmUapi.CREATE_VM, 0));
            configureX86Machine(os, vm, nativeArena);

            MemorySegment memorySegment = nativeArena.allocate(configuration.memory().bytes(), HOST_PAGE_SIZE);
            memorySegment.fill((byte) 0);
            SegmentGuestMemory guestMemory = new SegmentGuestMemory(memorySegment);
            registerGuestMemory(os, vm, guestMemory, nativeArena);

            vcpu = new NativeFileDescriptor(os.ioctl(vm, KvmUapi.CREATE_VCPU, 0));
            configureCpuid(os, kvm, vcpu, nativeArena);
            int runMappingSize = os.ioctl(kvm, KvmUapi.GET_VCPU_MMAP_SIZE);
            MemorySegment runState = os.mapShared(vcpu, runMappingSize, runArena);

            return new KvmVirtualMachine(os, nativeArena, runArena, guestMemory, runState, kvm, vm, vcpu);
        } catch (Throwable throwable) {
            os.closeQuietly(vcpu);
            os.closeQuietly(vm);
            os.closeQuietly(kvm);
            closeArenaQuietly(runArena);
            closeArenaQuietly(nativeArena);
            throw throwable;
        }
    }

    @Override
    public synchronized void bootLinux(LinuxBootSpec boot) throws IOException {
        ensureOpen();
        if (running) {
            throw new IllegalStateException("Cannot load Linux while the vCPU is running");
        }
        if (booted) {
            throw new IllegalStateException("A guest has already been loaded");
        }
        Objects.requireNonNull(boot, "boot");

        LinuxKernelImage kernel = LinuxKernelImage.read(boot.kernel());
        byte[] initrd = boot.initrd().isPresent() ? Files.readAllBytes(boot.initrd().orElseThrow()) : null;
        LinuxBootLayout layout = new LinuxBootLoader().load(guestMemory, kernel, initrd, boot.commandLine());
        configureLinuxBootstrapCpu(layout);
        booted = true;
    }

    @Override
    public GuestMemory memory() {
        ensureOpen();
        return guestMemory;
    }

    @Override
    public VmExit runUntilExit() {
        ensureOpen();
        if (!booted) {
            throw new IllegalStateException("No guest has been loaded");
        }
        synchronized (this) {
            if (running) {
                throw new IllegalStateException("This single-vCPU machine is already running");
            }
            running = true;
        }
        try {
            while (true) {
                int result = operatingSystem.ioctlRestartable(vcpuDescriptor, KvmUapi.RUN);
                if (result == LinuxOperatingSystem.INTERRUPTED) {
                    continue;
                }
                int reason = runState.get(JAVA_INT, RUN_EXIT_REASON);
                return switch (reason) {
                    case KvmUapi.EXIT_IO -> parseIoExit();
                    case KvmUapi.EXIT_MMIO -> parseMmioExit();
                    case KvmUapi.EXIT_HALT -> new VmExit.Halt(
                            runState.get(JAVA_BYTE, RUN_INTERRUPT_FLAG) != 0);
                    case KvmUapi.EXIT_SHUTDOWN -> new VmExit.Shutdown();
                    case KvmUapi.EXIT_SYSTEM_EVENT -> systemEventExit();
                    default -> new VmExit.Unknown(reason);
                };
            }
        } finally {
            synchronized (this) {
                running = false;
            }
        }
    }

    private VmExit.SystemEvent systemEventExit() {
        int backendCode = runState.get(JAVA_INT, RUN_SYSTEM_EVENT_TYPE);
        VmExit.SystemEventKind kind = switch (backendCode) {
            case KvmUapi.SYSTEM_EVENT_SHUTDOWN -> VmExit.SystemEventKind.SHUTDOWN;
            case KvmUapi.SYSTEM_EVENT_RESET -> VmExit.SystemEventKind.RESET;
            case KvmUapi.SYSTEM_EVENT_CRASH -> VmExit.SystemEventKind.CRASH;
            default -> VmExit.SystemEventKind.UNKNOWN;
        };
        return new VmExit.SystemEvent(
                kind, backendCode, runState.get(JAVA_LONG_UNALIGNED, RUN_SYSTEM_EVENT_FLAGS));
    }

    @Override
    public void pulseInterrupt(int interrupt) {
        setInterruptLine(interrupt, true);
        setInterruptLine(interrupt, false);
    }

    @Override
    public void setInterruptLine(int interrupt, boolean asserted) {
        ensureOpen();
        if (interrupt < 0 || interrupt > 23) {
            throw new IllegalArgumentException("Legacy x86 GSI must be in [0, 23]: " + interrupt);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment level = arena.allocate(IRQ_LEVEL_SIZE, Integer.BYTES);
            level.set(JAVA_INT, 0, interrupt);
            level.set(JAVA_INT, Integer.BYTES, asserted ? 1 : 0);
            operatingSystem.ioctl(vmDescriptor, KvmUapi.IRQ_LINE, level);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (running) {
            throw new IllegalStateException("Cannot close a vCPU from another thread while KVM_RUN is active");
        }
        closed = true;
        try {
            runArena.close();
        } finally {
            operatingSystem.closeQuietly(vcpuDescriptor);
            operatingSystem.closeQuietly(vmDescriptor);
            operatingSystem.closeQuietly(kvmDescriptor);
            vcpuDescriptor = null;
            vmDescriptor = null;
            kvmDescriptor = null;
            nativeArena.close();
        }
    }

    private VmExit.Io parseIoExit() {
        int directionValue = Byte.toUnsignedInt(runState.get(JAVA_BYTE, RUN_IO_DIRECTION));
        int elementSize = Byte.toUnsignedInt(runState.get(JAVA_BYTE, RUN_IO_SIZE));
        int port = Short.toUnsignedInt(runState.get(JAVA_SHORT_UNALIGNED, RUN_IO_PORT));
        long count = Integer.toUnsignedLong(runState.get(JAVA_INT_UNALIGNED, RUN_IO_COUNT));
        long dataOffset = runState.get(JAVA_LONG_UNALIGNED, RUN_IO_DATA_OFFSET);
        long byteCount = Math.multiplyExact((long) elementSize, count);
        if (dataOffset < 0 || byteCount > runState.byteSize() || dataOffset > runState.byteSize() - byteCount) {
            throw new IllegalStateException("KVM_EXIT_IO payload lies outside the kvm_run mapping");
        }
        VmExit.IoDirection direction = switch (directionValue) {
            case KvmUapi.EXIT_IO_IN -> VmExit.IoDirection.IN;
            case KvmUapi.EXIT_IO_OUT -> VmExit.IoDirection.OUT;
            default -> throw new IllegalStateException("Unknown KVM I/O direction: " + directionValue);
        };
        return new VmExit.Io(direction, port, elementSize, count,
                runState.asSlice(dataOffset, byteCount));
    }

    private VmExit.Mmio parseMmioExit() {
        long address = runState.get(JAVA_LONG_UNALIGNED, RUN_MMIO_PHYSICAL_ADDRESS);
        int length = runState.get(JAVA_INT, RUN_MMIO_LENGTH);
        if (length < 1 || length > Long.BYTES) {
            throw new IllegalStateException("KVM_EXIT_MMIO length is outside [1, 8]: " + length);
        }
        boolean write = runState.get(JAVA_BYTE, RUN_MMIO_IS_WRITE) != 0;
        return new VmExit.Mmio(address, write, runState.asSlice(RUN_MMIO_DATA, length));
    }

    private void configureLinuxBootstrapCpu(LinuxBootLayout layout) {
        guestMemory.segment().set(JAVA_LONG_UNALIGNED, BOOT_GDT_ADDRESS + 2L * Long.BYTES,
                BOOT_CODE_DESCRIPTOR);
        guestMemory.segment().set(JAVA_LONG_UNALIGNED, BOOT_GDT_ADDRESS + 3L * Long.BYTES,
                BOOT_DATA_DESCRIPTOR);

        MemorySegment special = nativeArena.allocate(SPECIAL_REGISTERS);
        operatingSystem.ioctl(vcpuDescriptor, KvmUapi.GET_SREGS, special);
        special.set(JAVA_LONG, SPECIAL_CR0,
                special.get(JAVA_LONG, SPECIAL_CR0) | KvmUapi.CR0_PROTECTION_ENABLE);
        special.set(JAVA_LONG, SPECIAL_EFER, 0);
        writeSegment(special, SPECIAL_CS, BOOT_CODE_SELECTOR, 0x0b);
        writeSegment(special, SPECIAL_DS, BOOT_DATA_SELECTOR, 0x03);
        copySegment(special, SPECIAL_DS, SPECIAL_ES);
        copySegment(special, SPECIAL_DS, SPECIAL_FS);
        copySegment(special, SPECIAL_DS, SPECIAL_GS);
        copySegment(special, SPECIAL_DS, SPECIAL_SS);
        MemorySegment gdt = special.asSlice(SPECIAL_GDT, DESCRIPTOR_TABLE.byteSize());
        gdt.set(JAVA_LONG, DESCRIPTOR_TABLE_BASE, BOOT_GDT_ADDRESS);
        gdt.set(JAVA_SHORT, DESCRIPTOR_TABLE_LIMIT, (short) (4 * Long.BYTES - 1));
        operatingSystem.ioctl(vcpuDescriptor, KvmUapi.SET_SREGS, special);

        MemorySegment registers = nativeArena.allocate(REGISTERS);
        registers.fill((byte) 0);
        registers.set(JAVA_LONG, REGISTERS_RFLAGS, KvmUapi.INITIAL_RFLAGS);
        registers.set(JAVA_LONG, REGISTERS_RIP, layout.kernel());
        registers.set(JAVA_LONG, REGISTERS_RSI, layout.bootParameters());
        registers.set(JAVA_LONG, REGISTERS_RBX, 0);
        registers.set(JAVA_LONG, REGISTERS_RBP, 0);
        registers.set(JAVA_LONG, REGISTERS_RDI, 0);
        operatingSystem.ioctl(vcpuDescriptor, KvmUapi.SET_REGS, registers);
    }

    private static void configureX86Machine(
            LinuxOperatingSystem os,
            NativeFileDescriptor vm,
            Arena arena
    ) {
        os.ioctl(vm, KvmUapi.SET_TSS_ADDRESS, KvmUapi.TSS_ADDRESS);
        MemorySegment identityMap = arena.allocate(JAVA_LONG);
        identityMap.set(JAVA_LONG, 0, KvmUapi.IDENTITY_MAP_ADDRESS);
        os.ioctl(vm, KvmUapi.SET_IDENTITY_MAP_ADDRESS, identityMap);
        os.ioctl(vm, KvmUapi.CREATE_IRQ_CHIP);
        MemorySegment pitConfiguration = arena.allocate(PIT_CONFIGURATION_SIZE, Long.BYTES);
        pitConfiguration.fill((byte) 0);
        os.ioctl(vm, KvmUapi.CREATE_PIT2, pitConfiguration);
    }

    private static void registerGuestMemory(
            LinuxOperatingSystem os,
            NativeFileDescriptor vm,
            SegmentGuestMemory memory,
            Arena arena
    ) {
        MemorySegment region = arena.allocate(USER_MEMORY_REGION);
        region.fill((byte) 0);
        region.set(JAVA_INT, 0, 0); // Memory-slot zero.
        region.set(JAVA_INT, 4, 0); // No KVM_MEM_* flags.
        region.set(JAVA_LONG, 8, 0); // Guest-physical base address.
        region.set(JAVA_LONG, 16, memory.byteSize());
        region.set(JAVA_LONG, 24, memory.segment().address());
        os.ioctl(vm, KvmUapi.SET_USER_MEMORY_REGION, region);
    }

    private static void configureCpuid(
            LinuxOperatingSystem os,
            NativeFileDescriptor kvm,
            NativeFileDescriptor vcpu,
            Arena arena
    ) {
        long bytes = CPUID_HEADER_SIZE + Math.multiplyExact((long) CPUID_MAX_ENTRIES, CPUID_ENTRY_SIZE);
        MemorySegment cpuid = arena.allocate(bytes, Long.BYTES);
        cpuid.fill((byte) 0);
        cpuid.set(JAVA_INT, 0, CPUID_MAX_ENTRIES);
        os.ioctl(kvm, KvmUapi.GET_SUPPORTED_CPUID, cpuid);
        os.ioctl(vcpu, KvmUapi.SET_CPUID2, cpuid);
    }

    private static void writeSegment(MemorySegment special, long offset, int selector, int type) {
        MemorySegment segment = special.asSlice(offset, SEGMENT.byteSize());
        segment.fill((byte) 0);
        segment.set(JAVA_LONG, SEGMENT_BASE, 0);
        segment.set(JAVA_INT, SEGMENT_LIMIT, 0xffff_ffff);
        segment.set(JAVA_SHORT, SEGMENT_SELECTOR, (short) selector);
        segment.set(JAVA_BYTE, SEGMENT_TYPE, (byte) type);
        segment.set(JAVA_BYTE, SEGMENT_PRESENT, (byte) 1);
        segment.set(JAVA_BYTE, SEGMENT_DPL, (byte) 0);
        segment.set(JAVA_BYTE, SEGMENT_DB, (byte) 1);
        segment.set(JAVA_BYTE, SEGMENT_S, (byte) 1);
        segment.set(JAVA_BYTE, SEGMENT_LONG, (byte) 0);
        segment.set(JAVA_BYTE, SEGMENT_GRANULARITY, (byte) 1);
    }

    private static void copySegment(MemorySegment special, long source, long destination) {
        special.asSlice(destination, SEGMENT.byteSize())
                .copyFrom(special.asSlice(source, SEGMENT.byteSize()));
    }

    private static void requireSupportedHostAndConfiguration(VirtualMachineConfiguration configuration) {
        HostPlatform host = HostPlatform.current();
        if (host.operatingSystem() != OperatingSystem.LINUX || host.architecture() != CpuArchitecture.X86_64) {
            throw new UnsupportedOperationException("KVM backend requires Linux x86-64; detected "
                    + host.displayName());
        }
        if (configuration.guestArchitecture() != CpuArchitecture.X86_64) {
            throw new UnsupportedOperationException("KVM backend currently supports x86-64 guests only");
        }
        if (configuration.virtualCpuCount() != 1) {
            throw new UnsupportedOperationException("KVM backend currently supports exactly one vCPU");
        }
        long memory = configuration.memory().bytes();
        if (memory < MINIMUM_GUEST_MEMORY || memory > MAXIMUM_GUEST_MEMORY) {
            throw new IllegalArgumentException("Guest RAM must be between 64 MiB and 3 GiB");
        }
        if ((memory & (HOST_PAGE_SIZE - 1)) != 0) {
            throw new IllegalArgumentException("Guest RAM must be aligned to 4096 bytes");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("KVM virtual machine is closed");
        }
    }

    private static void closeArenaQuietly(Arena arena) {
        try {
            arena.close();
        } catch (RuntimeException ignored) {
            // Preserve the failure that caused construction rollback.
        }
    }
}
