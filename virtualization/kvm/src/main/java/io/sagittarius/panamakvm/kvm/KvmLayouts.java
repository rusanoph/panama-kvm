package io.sagittarius.panamakvm.kvm;

import java.lang.foreign.MemoryLayout;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/** Exact x86-64 KVM UAPI layouts and field offsets used by the backend. */
final class KvmLayouts {
    /** {@code struct kvm_userspace_memory_region}, 32 bytes on x86-64. */
    static final MemoryLayout USER_MEMORY_REGION = MemoryLayout.structLayout(
            JAVA_INT.withName("slot"),
            JAVA_INT.withName("flags"),
            JAVA_LONG.withName("guest_phys_addr"),
            JAVA_LONG.withName("memory_size"),
            JAVA_LONG.withName("userspace_addr")
    ).withByteAlignment(Long.BYTES);

    /** {@code struct kvm_regs}: eighteen unsigned 64-bit register fields. */
    static final MemoryLayout REGISTERS = MemoryLayout.sequenceLayout(18, JAVA_LONG)
            .withByteAlignment(Long.BYTES);
    /** Offset of RBX in {@link #REGISTERS}. */
    static final long REGISTERS_RBX = 8;
    /** Offset of RSI in {@link #REGISTERS}. */
    static final long REGISTERS_RSI = 32;
    /** Offset of RDI in {@link #REGISTERS}. */
    static final long REGISTERS_RDI = 40;
    /** Offset of RSP in {@link #REGISTERS}. */
    static final long REGISTERS_RSP = 48;
    /** Offset of RBP in {@link #REGISTERS}. */
    static final long REGISTERS_RBP = 56;
    /** Offset of RIP in {@link #REGISTERS}. */
    static final long REGISTERS_RIP = 128;
    /** Offset of RFLAGS in {@link #REGISTERS}. */
    static final long REGISTERS_RFLAGS = 136;

    /** {@code struct kvm_segment}, including explicit UAPI padding. */
    static final MemoryLayout SEGMENT = MemoryLayout.structLayout(
            JAVA_LONG.withName("base"), JAVA_INT.withName("limit"), JAVA_SHORT.withName("selector"),
            JAVA_BYTE.withName("type"), JAVA_BYTE.withName("present"), JAVA_BYTE.withName("dpl"),
            JAVA_BYTE.withName("db"), JAVA_BYTE.withName("s"), JAVA_BYTE.withName("l"),
            JAVA_BYTE.withName("g"), JAVA_BYTE.withName("avl"), JAVA_BYTE.withName("unusable"),
            JAVA_BYTE.withName("padding")
    ).withByteAlignment(Long.BYTES);
    /** Segment base offset. */
    static final long SEGMENT_BASE = 0;
    /** Segment limit offset. */
    static final long SEGMENT_LIMIT = 8;
    /** Segment selector offset. */
    static final long SEGMENT_SELECTOR = 12;
    /** Segment access type offset. */
    static final long SEGMENT_TYPE = 14;
    /** Segment present-bit offset. */
    static final long SEGMENT_PRESENT = 15;
    /** Segment descriptor privilege level offset. */
    static final long SEGMENT_DPL = 16;
    /** Segment default operand-size bit offset. */
    static final long SEGMENT_DB = 17;
    /** Segment code/data bit offset. */
    static final long SEGMENT_S = 18;
    /** Segment long-mode bit offset. */
    static final long SEGMENT_LONG = 19;
    /** Segment page-granularity bit offset. */
    static final long SEGMENT_GRANULARITY = 20;

    /** {@code struct kvm_dtable}, 16 bytes including UAPI padding. */
    static final MemoryLayout DESCRIPTOR_TABLE = MemoryLayout.structLayout(
            JAVA_LONG.withName("base"), JAVA_SHORT.withName("limit"),
            MemoryLayout.sequenceLayout(3, JAVA_SHORT).withName("padding")
    ).withByteAlignment(Long.BYTES);
    /** Descriptor-table base offset. */
    static final long DESCRIPTOR_TABLE_BASE = 0;
    /** Descriptor-table limit offset. */
    static final long DESCRIPTOR_TABLE_LIMIT = 8;

    /** Complete x86 {@code struct kvm_sregs}. */
    static final MemoryLayout SPECIAL_REGISTERS = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(8, SEGMENT).withName("segments"),
            MemoryLayout.sequenceLayout(2, DESCRIPTOR_TABLE).withName("dtables"),
            MemoryLayout.sequenceLayout(7, JAVA_LONG).withName("control"),
            MemoryLayout.sequenceLayout(4, JAVA_LONG).withName("interrupt_bitmap")
    ).withByteAlignment(Long.BYTES);
    /** CS segment offset. */
    static final long SPECIAL_CS = 0;
    /** DS segment offset. */
    static final long SPECIAL_DS = 24;
    /** ES segment offset. */
    static final long SPECIAL_ES = 48;
    /** FS segment offset. */
    static final long SPECIAL_FS = 72;
    /** GS segment offset. */
    static final long SPECIAL_GS = 96;
    /** SS segment offset. */
    static final long SPECIAL_SS = 120;
    /** GDT register offset. */
    static final long SPECIAL_GDT = 192;
    /** CR0 offset. */
    static final long SPECIAL_CR0 = 224;
    /** EFER offset. */
    static final long SPECIAL_EFER = 264;

    /** {@code kvm_run.exit_reason} offset. */
    static final long RUN_EXIT_REASON = 8;
    /** {@code kvm_run.if_flag}: guest interrupt-flag state on exit. */
    static final long RUN_INTERRUPT_FLAG = 13;
    /** {@code kvm_run.io.direction} offset. */
    static final long RUN_IO_DIRECTION = 32;
    /** {@code kvm_run.io.size} offset. */
    static final long RUN_IO_SIZE = 33;
    /** {@code kvm_run.io.port} offset. */
    static final long RUN_IO_PORT = 34;
    /** {@code kvm_run.io.count} offset. */
    static final long RUN_IO_COUNT = 36;
    /** {@code kvm_run.io.data_offset} offset. */
    static final long RUN_IO_DATA_OFFSET = 40;
    /** {@code kvm_run.mmio.phys_addr} offset. */
    static final long RUN_MMIO_PHYSICAL_ADDRESS = 32;
    /** {@code kvm_run.mmio.data} offset. */
    static final long RUN_MMIO_DATA = 40;
    /** {@code kvm_run.mmio.len} offset. */
    static final long RUN_MMIO_LENGTH = 48;
    /** {@code kvm_run.mmio.is_write} offset. */
    static final long RUN_MMIO_IS_WRITE = 52;
    /** {@code kvm_run.system_event.type} offset. */
    static final long RUN_SYSTEM_EVENT_TYPE = 32;
    /** {@code kvm_run.system_event.flags} offset. */
    static final long RUN_SYSTEM_EVENT_FLAGS = 40;

    /** Header size of {@code struct kvm_cpuid2}. */
    static final long CPUID_HEADER_SIZE = 8;
    /** Size of one {@code struct kvm_cpuid_entry2}. */
    static final long CPUID_ENTRY_SIZE = 40;
    /** Maximum CPUID entries requested from KVM. */
    static final int CPUID_MAX_ENTRIES = 256;

    /** {@code struct kvm_pit_config} size. */
    static final long PIT_CONFIGURATION_SIZE = 64;
    /** {@code struct kvm_irq_level} size. */
    static final long IRQ_LEVEL_SIZE = 8;

    static {
        requireSize("kvm_userspace_memory_region", USER_MEMORY_REGION, 32);
        requireSize("kvm_regs", REGISTERS, 144);
        requireSize("kvm_segment", SEGMENT, 24);
        requireSize("kvm_dtable", DESCRIPTOR_TABLE, 16);
        requireSize("kvm_sregs", SPECIAL_REGISTERS, 312);
    }

    private KvmLayouts() { }

    private static void requireSize(String name, MemoryLayout layout, long expected) {
        if (layout.byteSize() != expected) {
            throw new ExceptionInInitializerError(name + " layout size " + layout.byteSize()
                    + " does not match Linux UAPI size " + expected);
        }
    }
}
