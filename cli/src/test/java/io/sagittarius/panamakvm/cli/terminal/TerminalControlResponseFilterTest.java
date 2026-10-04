package io.sagittarius.panamakvm.cli.terminal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalControlResponseFilterTest {
    @Test
    void dropsTerminalCursorPositionReport() {
        List<Integer> received = new ArrayList<>();
        TerminalControlResponseFilter filter = new TerminalControlResponseFilter(received::add);

        offer(filter, "pwd\n\u001b[29;5R");
        filter.finish();

        assertEquals(List.of((int) 'p', (int) 'w', (int) 'd', (int) '\n'), received);
    }

    @Test
    void preservesArrowKeySequence() {
        List<Integer> received = new ArrayList<>();
        TerminalControlResponseFilter filter = new TerminalControlResponseFilter(received::add);

        offer(filter, "\u001b[A");
        filter.finish();

        assertEquals(List.of(0x1b, (int) '[', (int) 'A'), received);
    }

    @Test
    void preservesMalformedCursorReport() {
        List<Integer> received = new ArrayList<>();
        TerminalControlResponseFilter filter = new TerminalControlResponseFilter(received::add);

        String malformed = "\u001b[1;;2R";
        offer(filter, malformed);
        filter.finish();

        assertEquals(malformed.chars().boxed().toList(), received);
    }

    private static void offer(TerminalControlResponseFilter filter, String text) {
        text.chars().forEach(filter::accept);
    }
}
