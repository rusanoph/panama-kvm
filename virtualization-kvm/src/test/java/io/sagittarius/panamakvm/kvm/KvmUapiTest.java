package io.sagittarius.panamakvm.kvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KvmUapiTest {
    @Test
    void ioctlConstantsMatchLinuxIocEncoding() {
        assertEquals(KvmUapi.GET_API_VERSION, io(0xae, 0x00));
        assertEquals(KvmUapi.SET_USER_MEMORY_REGION, iow(0xae, 0x46, 32));
        assertEquals(KvmUapi.CREATE_PIT2, iow(0xae, 0x77, 64));
        assertEquals(KvmUapi.GET_REGS, ior(0xae, 0x81, 144));
        assertEquals(KvmUapi.SET_SREGS, iow(0xae, 0x84, 312));
    }

    private static long io(long type, long number) {
        return type << 8 | number;
    }

    private static long iow(long type, long number, long size) {
        return 1L << 30 | size << 16 | type << 8 | number;
    }

    private static long ior(long type, long number, long size) {
        return 2L << 30 | size << 16 | type << 8 | number;
    }
}
