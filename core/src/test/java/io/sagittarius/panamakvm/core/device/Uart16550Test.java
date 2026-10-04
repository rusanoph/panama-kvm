package io.sagittarius.panamakvm.core.device;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Uart16550Test {
    @Test
    void writesGuestTransmitterToHostOutput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Uart16550 uart = new Uart16550(output, ignored -> { });
        uart.write(Uart16550.COM1_BASE, 1, 'J');
        assertEquals("J", output.toString(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void flushesPromptWithoutWaitingForNewline() {
        ByteArrayOutputStream visibleOutput = new ByteArrayOutputStream();
        Uart16550 uart = new Uart16550(new BufferedOutputStream(visibleOutput), ignored -> { });

        uart.write(Uart16550.COM1_BASE, 1, '#');

        assertEquals("#", visibleOutput.toString(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void buffersInputAndPulsesReceiveInterrupt() {
        AtomicInteger irq = new AtomicInteger(-1);
        Uart16550 uart = new Uart16550(new ByteArrayOutputStream(), irq::set);
        uart.write(Uart16550.COM1_BASE + 1, 1, Uart16550.INTERRUPT_ENABLE_RECEIVED_DATA);
        uart.offerInput('x');
        assertEquals(Uart16550.COM1_INTERRUPT, irq.get());
        assertEquals(Uart16550.LINE_STATUS_DATA_READY,
                uart.read(Uart16550.COM1_BASE + 5, 1) & Uart16550.LINE_STATUS_DATA_READY);
        assertEquals('x', uart.read(Uart16550.COM1_BASE, 1));
    }

    @Test
    void holdsReceiveInterruptUntilInputIsConsumed() {
        List<Boolean> levels = new ArrayList<>();
        Uart16550 uart = new Uart16550(new ByteArrayOutputStream(), ignored -> { },
                (ignored, asserted) -> levels.add(asserted));
        uart.write(Uart16550.COM1_BASE + 1, 1, Uart16550.INTERRUPT_ENABLE_RECEIVED_DATA);

        uart.offerInput('x');
        assertEquals(List.of(true), levels);

        uart.read(Uart16550.COM1_BASE, 1);
        assertEquals(List.of(true, false), levels);
    }

    @Test
    void supportsDivisorLatch() {
        Uart16550 uart = new Uart16550(new ByteArrayOutputStream(), ignored -> { });
        uart.write(Uart16550.COM1_BASE + 3, 1, Uart16550.LINE_CONTROL_DIVISOR_LATCH);
        uart.write(Uart16550.COM1_BASE, 1, 3);
        uart.write(Uart16550.COM1_BASE + 1, 1, 1);
        assertEquals(3, uart.read(Uart16550.COM1_BASE, 1));
        assertEquals(1, uart.read(Uart16550.COM1_BASE + 1, 1));
    }

    @Test
    void advertises16550aFifoWhenEnabled() {
        Uart16550 uart = new Uart16550(new ByteArrayOutputStream(), ignored -> { });
        uart.write(Uart16550.COM1_BASE + 2, 1, Uart16550.FIFO_CONTROL_ENABLE);
        assertEquals(
                Uart16550.INTERRUPT_IDENTIFICATION_FIFO_CAPABLE
                        | Uart16550.INTERRUPT_IDENTIFICATION_NONE,
                uart.read(Uart16550.COM1_BASE + 2, 1));
    }
}
