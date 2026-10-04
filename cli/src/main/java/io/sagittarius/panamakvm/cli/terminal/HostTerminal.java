package io.sagittarius.panamakvm.cli.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporarily configures a host terminal for an interactive serial session.
 *
 * <p>The guest shell echoes and edits typed characters itself. The host must
 * deliver each byte immediately, pass terminal signals through as bytes, and avoid rendering terminal control replies
 * before {@link TerminalControlResponseFilter} can discard them. If standard
 * input is not a terminal, the operation is a no-op so redirected input remains
 * supported.</p>
 */
final class HostTerminal implements AutoCloseable {
    private static final HostTerminal UNCHANGED = new HostTerminal(null);

    private final String savedState;
    private final AtomicBoolean restored = new AtomicBoolean();
    private final Thread restoreHook;

    private HostTerminal(String savedState) {
        this.savedState = savedState;
        restoreHook = savedState == null ? null : Thread.ofPlatform()
                .name("panama-kvm-terminal-restore")
                .unstarted(this::restore);
        if (restoreHook != null) {
            Runtime.getRuntime().addShutdownHook(restoreHook);
        }
    }

    static HostTerminal forInteractiveSession(boolean interactive) {
        if (!interactive) {
            return UNCHANGED;
        }

        String state = readState();
        // Keep Ctrl+C as byte 0x03 for the guest TTY. With "isig" enabled, the
        // host terminal sends SIGINT to this JVM instead and tears down the VM.
        if (state == null || !runStty("-icanon", "-echo", "-isig", "min", "1", "time", "0")) {
            if (state != null) {
                runStty(state);
            }
            return UNCHANGED;
        }
        return new HostTerminal(state);
    }

    @Override
    public void close() {
        restore();
        if (restoreHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(restoreHook);
            } catch (IllegalStateException ignored) {
                // The JVM is already shutting down and the hook will restore the terminal.
            }
        }
    }

    private void restore() {
        if (savedState != null && restored.compareAndSet(false, true)) {
            runStty(savedState);
        }
    }

    private static String readState() {
        try {
            Process process = new ProcessBuilder("stty", "-g")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String state = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
            return process.waitFor() == 0 && !state.isEmpty() ? state : null;
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static boolean runStty(String... arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add("stty");
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
