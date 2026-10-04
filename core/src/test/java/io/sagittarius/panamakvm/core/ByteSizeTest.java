package io.sagittarius.panamakvm.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteSizeTest {
    @Test
    void convertsMebibytesExactly() {
        assertEquals(256L << 20, ByteSize.ofMiB(256).bytes());
    }

    @Test
    void alignsUpWithoutChangingAlignedValues() {
        assertEquals(8192, ByteSize.ofBytes(4097).alignUp(4096).bytes());
        assertEquals(4096, ByteSize.ofBytes(4096).alignUp(4096).bytes());
    }

    @Test
    void rejectsNonPowerOfTwoAlignment() {
        assertThrows(IllegalArgumentException.class, () -> ByteSize.ofBytes(1).alignUp(3));
    }
}
