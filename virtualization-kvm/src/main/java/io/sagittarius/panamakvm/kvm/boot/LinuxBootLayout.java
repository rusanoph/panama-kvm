package io.sagittarius.panamakvm.kvm.boot;

/**
 * Guest-physical addresses selected by the direct Linux loader.
 *
 * @param bootParameters 4 KiB zero-page address
 * @param commandLine NUL-terminated kernel command-line address
 * @param kernel protected-mode kernel load/entry address
 * @param initrd initramfs address, or zero when absent
 * @param initrdSize initramfs size, or zero when absent
 */
public record LinuxBootLayout(
        long bootParameters,
        long commandLine,
        long kernel,
        long initrd,
        long initrdSize
) {
    /** Traditional low-memory zero-page address used by PanamaKVM. */
    public static final long BOOT_PARAMETERS_ADDRESS = 0x0000_7000L;
    /** Low-memory command-line buffer address. */
    public static final long COMMAND_LINE_ADDRESS = 0x0002_0000L;
    /** Standard bzImage protected-mode load address. */
    public static final long KERNEL_LOAD_ADDRESS = 0x0010_0000L;
    /** x86 Linux {@code struct boot_params} size. */
    public static final long BOOT_PARAMETERS_SIZE = 4096;
    /** Page alignment used for the initramfs. */
    public static final long INITRD_ALIGNMENT = 4096;
}
