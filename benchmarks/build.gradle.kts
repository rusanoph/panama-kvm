plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":virtualization-kvm"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}

application {
    mainClass = "org.openjdk.jmh.Main"
}

tasks.test {
    enabled = false
}
