package io.sagittarius.panamakvm.core.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoPortRangeTest {
    @Test
    void supportsLastPortInUnsignedSpace() {
        IoPortRange range = new IoPortRange(0xffff, 1);

        assertTrue(range.contains(0xffff));
        assertFalse(range.contains(0x10000));
    }

    @Test
    void rejectsRangePastUnsignedSpace() {
        assertThrows(IllegalArgumentException.class, () -> new IoPortRange(0xffff, 2));
    }
}
