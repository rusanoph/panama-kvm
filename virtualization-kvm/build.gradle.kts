plugins {
    id("panamakvm.auto-service")
}

dependencies {
    api(project(":virtualization-api"))
    implementation(project(":platform-linux"))
}

tasks.register<Test>("kvmTest") {
    description = "Runs Linux KVM integration tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("kvm")
    }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    shouldRunAfter(tasks.test)
}
