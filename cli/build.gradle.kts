plugins {
    application
}

dependencies {
    implementation(project(":virtualization-api"))
    runtimeOnly(project(":virtualization-sim"))
    runtimeOnly(project(":virtualization-kvm"))
}

application {
    applicationName = "panama-kvm"
    mainClass = "io.sagittarius.panamakvm.cli.PanamaKvm"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    manifest.attributes("Main-Class" to application.mainClass.get())
}
