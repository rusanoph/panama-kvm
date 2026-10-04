rootProject.name = "panama-kvm"

include(
    "core",
    "platform-api",
    "platform-linux",
    "virtualization-api",
    "virtualization-sim",
    "virtualization-kvm",
    "cli",
    "benchmarks"
)

project(":virtualization-api").projectDir = file("virtualization/api")
project(":virtualization-kvm").projectDir = file("virtualization/kvm")
project(":virtualization-sim").projectDir = file("virtualization/sim")

project(":platform-api").projectDir = file("platform/api")
project(":platform-linux").projectDir = file("platform/linux")
