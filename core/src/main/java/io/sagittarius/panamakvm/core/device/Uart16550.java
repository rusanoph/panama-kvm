package io.sagittarius.panamakvm.core.device;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntConsumer;

/**
 * Minimal 16550-compatible UART suitable for a Linux serial console. It models
 * the eight legacy registers, divisor-latch access, receive buffering, and IRQ
 * notification while intentionally omitting host timing simulation.
 */
public final class Uart16550 implements IoPortDevice {
    /** Conventional COM1 base I/O port. */
    public static final int COM1_BASE = 0x3f8;
    /** Conventional COM1 interrupt request line. */
    public static final int COM1_INTERRUPT = 4;
    /** Number of consecutive I/O ports occupied by a 16550 UART. */
    public static final int PORT_COUNT = 8;

    /** Receiver-data-available bit in the line-status register. */
    public static final int LINE_STATUS_DATA_READY = 1;
    /** Transmitter-holding-register-empty bit in the line-status register. */
    public static final int LINE_STATUS_TRANSMITTER_EMPTY = 1 << 5;
    /** Transmitter-empty bit in the line-status register. */
    public static final int LINE_STATUS_TRANSMITTER_IDLE = 1 << 6;
    /** Divisor-latch-access bit in the line-control register. */
    public static final int LINE_CONTROL_DIVISOR_LATCH = 1 << 7;
    /** Receive-data interrupt-enable bit. */
    public static final int INTERRUPT_ENABLE_RECEIVED_DATA = 1;
    /** Transmitter-empty interrupt-enable bit. */
    public static final int INTERRUPT_ENABLE_TRANSMITTER_EMPTY = 1 << 1;

    /** FIFO-control bit that enables the 16550A transmit and receive FIFOs. */
    public static final int FIFO_CONTROL_ENABLE = 1;
    /** FIFO-control command that resets the receive FIFO. */
    public static final int FIFO_CONTROL_CLEAR_RECEIVE = 1 << 1;
    /** IIR capability bits reported by a functioning 16550A FIFO. */
    public static final int INTERRUPT_IDENTIFICATION_FIFO_CAPABLE = 0xc0;
    /** IIR code indicating that no interrupt is pending. */
    public static final int INTERRUPT_IDENTIFICATION_NONE = 1;
    /** IIR code indicating a transmitter-holding-register-empty interrupt. */
    public static final int INTERRUPT_IDENTIFICATION_TRANSMITTER_EMPTY = 2;
    /** IIR code indicating a receive-data-available interrupt. */
    public static final int INTERRUPT_IDENTIFICATION_RECEIVED_DATA = 4;

    private final int basePort;
    private final List<IoPortRange> ranges;
    private final OutputStream output;
    private final IntConsumer interruptSink;
    private final InterruptLineSink receiveInterruptLine;
    private final Queue<Integer> receivedBytes = new ConcurrentLinkedQueue<>();

    private int interruptEnable;
    private int lineControl;
    private int modemControl;
    private int scratch;
    private int divisorLow = 1;
    private int divisorHigh;
    private boolean fifoEnabled;
    private boolean receiveInterruptAsserted;

    /**
     * Creates a COM1-compatible UART.
     *
     * @param output host sink for guest serial output
     * @param interruptSink callback receiving the IRQ number to pulse
     */
    public Uart16550(OutputStream output, IntConsumer interruptSink) {
        this(COM1_BASE, output, interruptSink, (interrupt, asserted) -> {
            if (asserted) {
                interruptSink.accept(interrupt);
            }
        });
    }

    /**
     * Creates a COM1-compatible UART with a level-triggered receive line.
     *
     * @param output host sink for guest serial output
     * @param interruptSink callback receiving interrupt pulses
     * @param receiveInterruptLine callback receiving receive-line level changes
     */
    public Uart16550(OutputStream output, IntConsumer interruptSink, InterruptLineSink receiveInterruptLine) {
        this(COM1_BASE, output, interruptSink, receiveInterruptLine);
    }

    /**
     * Creates a UART at a custom eight-port base.
     *
     * @param basePort first I/O port
     * @param output host sink for guest serial output
     * @param interruptSink callback receiving the IRQ number to pulse
     */
    public Uart16550(int basePort, OutputStream output, IntConsumer interruptSink) {
        this(basePort, output, interruptSink, (interrupt, asserted) -> {
            if (asserted) {
                interruptSink.accept(interrupt);
            }
        });
    }

    /**
     * Creates a UART at a custom base with a level-triggered receive line.
     *
     * @param basePort first I/O port
     * @param output host sink for guest serial output
     * @param interruptSink callback receiving interrupt pulses
     * @param receiveInterruptLine callback receiving receive-line level changes
     */
    public Uart16550(
            int basePort,
            OutputStream output,
            IntConsumer interruptSink,
            InterruptLineSink receiveInterruptLine
    ) {
        if (basePort < 0 || basePort + PORT_COUNT - 1 > 0xffff) {
            throw new IllegalArgumentException("UART port range lies outside unsigned 16-bit I/O space");
        }
        this.basePort = basePort;
        this.ranges = List.of(new IoPortRange(basePort, PORT_COUNT));
        this.output = Objects.requireNonNull(output, "output");
        this.interruptSink = Objects.requireNonNull(interruptSink, "interruptSink");
        this.receiveInterruptLine = Objects.requireNonNull(receiveInterruptLine, "receiveInterruptLine");
    }

    /**
     * Adds one host byte to the UART receive FIFO and signals the guest when
     * receive interrupts are enabled.
     *
     * @param value unsigned byte value
     */
    public synchronized void offerInput(int value) {
        if ((value & ~0xff) != 0) {
            throw new IllegalArgumentException("UART input must be an unsigned byte: " + value);
        }
        receivedBytes.add(value);
        updateReceiveInterruptLine();
    }

    @Override
    public List<IoPortRange> ranges() {
        return ranges;
    }

    @Override
    public synchronized long read(int port, int size) {
        requireByteAccess(port, size);
        int register = port - basePort;
        return switch (register) {
            case 0 -> divisorLatchEnabled() ? divisorLow : readReceivedByte();
            case 1 -> divisorLatchEnabled() ? divisorHigh : interruptEnable;
            case 2 -> interruptIdentification();
            case 3 -> lineControl;
            case 4 -> modemControl;
            case 5 -> lineStatus();
            case 6 -> 0xb0; // CTS, DSR, and DCD asserted; no delta bits.
            case 7 -> scratch;
            default -> throw new AssertionError("Validated UART register outside range");
        };
    }

    @Override
    public synchronized void write(int port, int size, long value) {
        requireByteAccess(port, size);
        int byteValue = (int) value & 0xff;
        int register = port - basePort;
        switch (register) {
            case 0 -> {
                if (divisorLatchEnabled()) {
                    divisorLow = byteValue;
                } else {
                    writeTransmitter(byteValue);
                }
            }
            case 1 -> {
                if (divisorLatchEnabled()) {
                    divisorHigh = byteValue;
                } else {
                    interruptEnable = byteValue & 0x0f;
                    if ((interruptEnable & INTERRUPT_ENABLE_TRANSMITTER_EMPTY) != 0) {
                        interruptSink.accept(COM1_INTERRUPT);
                    }
                    updateReceiveInterruptLine();
                }
            }
            case 2 -> {
                fifoEnabled = (byteValue & FIFO_CONTROL_ENABLE) != 0;
                if ((byteValue & FIFO_CONTROL_CLEAR_RECEIVE) != 0) {
                    receivedBytes.clear();
                    updateReceiveInterruptLine();
                }
            }
            case 3 -> lineControl = byteValue;
            case 4 -> modemControl = byteValue;
            case 5, 6 -> { /* Read-only in the subset needed by Linux. */ }
            case 7 -> scratch = byteValue;
            default -> throw new AssertionError("Validated UART register outside range");
        }
    }

    @Override
    public void close() {
        try {
            output.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not flush UART output", exception);
        }
    }

    private int readReceivedByte() {
        Integer value = receivedBytes.poll();
        updateReceiveInterruptLine();
        return value == null ? 0 : value;
    }

    private int interruptIdentification() {
        int capability = fifoEnabled ? INTERRUPT_IDENTIFICATION_FIFO_CAPABLE : 0;
        if (!receivedBytes.isEmpty() && (interruptEnable & INTERRUPT_ENABLE_RECEIVED_DATA) != 0) {
            return capability | INTERRUPT_IDENTIFICATION_RECEIVED_DATA;
        }
        if ((interruptEnable & INTERRUPT_ENABLE_TRANSMITTER_EMPTY) != 0) {
            return capability | INTERRUPT_IDENTIFICATION_TRANSMITTER_EMPTY;
        }
        return capability | INTERRUPT_IDENTIFICATION_NONE;
    }

    private int lineStatus() {
        int status = LINE_STATUS_TRANSMITTER_EMPTY | LINE_STATUS_TRANSMITTER_IDLE;
        return receivedBytes.isEmpty() ? status : status | LINE_STATUS_DATA_READY;
    }

    private boolean divisorLatchEnabled() {
        return (lineControl & LINE_CONTROL_DIVISOR_LATCH) != 0;
    }

    private void updateReceiveInterruptLine() {
        boolean asserted = !receivedBytes.isEmpty()
                && (interruptEnable & INTERRUPT_ENABLE_RECEIVED_DATA) != 0;
        if (receiveInterruptAsserted != asserted) {
            receiveInterruptAsserted = asserted;
            receiveInterruptLine.setLevel(COM1_INTERRUPT, asserted);
        }
    }

    private void writeTransmitter(int value) {
        try {
            output.write(value);
            output.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write guest UART output", exception);
        }
    }

    private void requireByteAccess(int port, int size) {
        if (!handles(port)) {
            throw new IllegalArgumentException("Port is outside this UART: 0x" + Integer.toHexString(port));
        }
        if (size != 1) {
            throw new IllegalArgumentException("16550 register access must be one byte, got " + size);
        }
    }
}
