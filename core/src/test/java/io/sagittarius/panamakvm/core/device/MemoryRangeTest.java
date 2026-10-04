package io.sagittarius.panamakvm.core.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRangeTest {
    @Test
    void containsOnlyCompleteTransfers() {
        MemoryRange range = new MemoryRange(0x1000, 0x100);

        assertTrue(range.contains(0x10fc, 4));
        assertFalse(range.contains(0x10fd, 4));
    }

    @Test
    void rejectsOverflowingEndAddress() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryRange(Long.MAX_VALUE, 1));
    }
}
