package io.sagittarius.panamakvm.core.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmosRtcTest {
    @Test
    void reportsRtcRegisterAWithoutUpdateInProgress() {
        CmosRtc rtc = new CmosRtc();
        rtc.write(CmosRtc.INDEX_PORT, 1, 0x0a);

        assertEquals(0, rtc.read(CmosRtc.DATA_PORT, 1) & 0x80);
    }

    @Test
    void retainsWritableCalendarRegisters() {
        CmosRtc rtc = new CmosRtc();
        rtc.write(CmosRtc.INDEX_PORT, 1, 0x09);
        rtc.write(CmosRtc.DATA_PORT, 1, 42);

        assertEquals(42, rtc.read(CmosRtc.DATA_PORT, 1));
    }
}
