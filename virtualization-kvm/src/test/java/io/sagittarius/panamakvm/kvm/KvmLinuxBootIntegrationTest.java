package io.sagittarius.panamakvm.kvm;

import io.sagittarius.panamakvm.core.ByteSize;
import io.sagittarius.panamakvm.core.device.AbsentIoPortDevice;
import io.sagittarius.panamakvm.core.device.AbsentMemoryMappedDevice;
import io.sagittarius.panamakvm.core.device.CmosRtc;
import io.sagittarius.panamakvm.core.device.IoPortBus;
import io.sagittarius.panamakvm.core.device.MemoryMappedIoBus;
import io.sagittarius.panamakvm.core.device.Uart16550;
import io.sagittarius.panamakvm.virtualization.VirtualMachineConfiguration;
import io.sagittarius.panamakvm.virtualization.VmRunPolicy;
import io.sagittarius.panamakvm.virtualization.VmRunResult;
import io.sagittarius.panamakvm.virtualization.VmRunner;
import io.sagittarius.panamakvm.virtualization.linux.LinuxBootSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("kvm")
@EnabledOnOs(OS.LINUX)
class KvmLinuxBootIntegrationTest {
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void bootsRealLinuxUserspaceAndShutsDown() throws Exception {
        Path kernel = environmentPath("PANAMAKVM_TEST_KERNEL");
        Path initrd = environmentPath("PANAMAKVM_TEST_INITRD");
        assumeTrue(Files.isReadable(Path.of("/dev/kvm")), "/dev/kvm is unavailable");
        assumeTrue(Files.isReadable(kernel), "test kernel is unavailable");
        assumeTrue(Files.isReadable(initrd), "test initramfs is unavailable");

        ByteArrayOutputStream serial = new ByteArrayOutputStream();
        try (KvmVirtualMachine machine = KvmVirtualMachine.create(
                VirtualMachineConfiguration.x86_64(ByteSize.ofMiB(256)))) {
            machine.bootLinux(new LinuxBootSpec(kernel, Optional.of(initrd),
                    "console=ttyS0,115200 rdinit=/init panic=-1 reboot=t"));
            Uart16550 uart = new Uart16550(serial, machine::pulseInterrupt, machine::setInterruptLine);
            try (IoPortBus io = new IoPortBus(List.of(uart, new CmosRtc()), new AbsentIoPortDevice());
                 MemoryMappedIoBus mmio = new MemoryMappedIoBus(
                         List.of(), new AbsentMemoryMappedDevice())) {
                assertEquals(VmRunResult.SHUTDOWN,
                        new VmRunner(machine, io, mmio, VmRunPolicy.operatingSystem()).run());
            }
        }
        assertTrue(serial.toString(StandardCharsets.UTF_8).contains("PANAMAKVM_SMOKE_OK"));
    }

    private static Path environmentPath(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), name + " is not set");
        return Path.of(value);
    }
}
