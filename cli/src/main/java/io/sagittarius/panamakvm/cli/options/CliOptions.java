package io.sagittarius.panamakvm.cli.options;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed command-line options for the PanamaKVM application.
 *
 * @param backend selected backend, or null for automatic selection
 * @param kernel Linux kernel image, or null for the simulated guest
 * @param initrd optional Linux initial RAM disk
 * @param memoryMib guest memory in MiB
 * @param commandLine Linux kernel command line
 * @param labels guest metadata labels
 * @param interactive whether to forward host terminal input
 * @param listBackends whether to print installed backends
 * @param help whether to print CLI help
 */
public record CliOptions(
        String backend,
        Path kernel,
        Path initrd,
        long memoryMib,
        String commandLine,
        Map<String, String> labels,
        boolean interactive,
        boolean listBackends,
        boolean help
) {
    /** Default guest RAM for direct Linux boot. */
    public static final long DEFAULT_MEMORY_MIB = 256;
    /** Distribution-neutral serial-console command line. */
    public static final String DEFAULT_COMMAND_LINE =
            "console=ttyS0,115200 earlycon=uart8250,io,0x3f8,115200n8 rdinit=/init panic=-1 reboot=t";

    /**
     * Parses and validates CLI arguments.
     *
     * @param arguments raw command-line arguments
     * @return parsed options
     */
    public static CliOptions parse(String[] arguments) {
        String backend = null;
        Path kernel = null;
        Path initrd = null;
        long memoryMib = DEFAULT_MEMORY_MIB;
        String commandLine = DEFAULT_COMMAND_LINE;
        Map<String, String> labels = new LinkedHashMap<>();
        boolean interactive = true;
        boolean listBackends = false;
        boolean help = false;

        for (int index = 0; index < arguments.length; index++) {
            switch (arguments[index]) {
                case "--backend" -> backend = requireValue(arguments, ++index, "--backend");
                case "--kernel" -> kernel = Path.of(requireValue(arguments, ++index, "--kernel"));
                case "--initrd" -> initrd = Path.of(requireValue(arguments, ++index, "--initrd"));
                case "--memory-mib" -> memoryMib = Long.parseLong(
                        requireValue(arguments, ++index, "--memory-mib"));
                case "--cmdline" -> commandLine = requireValue(arguments, ++index, "--cmdline");
                case "--label" -> parseLabel(requireValue(arguments, ++index, "--label"), labels);
                case "--non-interactive" -> interactive = false;
                case "--list-backends" -> listBackends = true;
                case "--help", "-h" -> help = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + arguments[index]);
            }
        }
        if (memoryMib <= 0) {
            throw new IllegalArgumentException("--memory-mib must be positive");
        }
        if (initrd != null && kernel == null) {
            throw new IllegalArgumentException("--initrd requires --kernel");
        }
        return new CliOptions(backend, kernel, initrd, memoryMib, commandLine,
                Map.copyOf(labels), interactive, listBackends, help);
    }

    private static String requireValue(String[] arguments, int index, String option) {
        if (index >= arguments.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return arguments[index];
    }

    private static void parseLabel(String text, Map<String, String> labels) {
        int separator = text.indexOf('=');
        if (separator <= 0 || separator == text.length() - 1) {
            throw new IllegalArgumentException("--label must have key=value form");
        }
        String key = text.substring(0, separator);
        if (labels.putIfAbsent(key, text.substring(separator + 1)) != null) {
            throw new IllegalArgumentException("Duplicate label: " + key);
        }
    }
}
