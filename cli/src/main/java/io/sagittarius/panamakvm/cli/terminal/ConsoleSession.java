package io.sagittarius.panamakvm.cli.terminal;

import io.sagittarius.panamakvm.core.device.Uart16550;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Connects host terminal input to a guest serial port for one VM run. */
public final class ConsoleSession implements AutoCloseable {
    private final HostTerminal terminal;

    private ConsoleSession(HostTerminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Opens a serial console session and starts forwarding input when interactive.
     *
     * @param uart guest serial port
     * @param interactive whether host input should be forwarded
     * @return console session whose terminal state must be restored on close
     */
    public static ConsoleSession open(Uart16550 uart, boolean interactive) {
        Objects.requireNonNull(uart, "uart");
        HostTerminal terminal = HostTerminal.forInteractiveSession(interactive);
        if (interactive) {
            startInputPump(uart);
        }
        return new ConsoleSession(terminal);
    }

    @Override
    public void close() {
        terminal.close();
    }

    private static void startInputPump(Uart16550 uart) {
        Thread.ofVirtual().name("panama-kvm-uart-input").start(() -> {
            try {
                TerminalControlResponseFilter input = new TerminalControlResponseFilter(uart::offerInput);
                int next;
                while ((next = System.in.read()) >= 0) {
                    input.accept(next);
                }
                input.finish();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            } catch (IllegalStateException ignored) {
                // The VM may close while the virtual input thread is still blocked.
            }
        });
    }
}
