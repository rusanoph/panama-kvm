import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("panamakvm.auto-service")
}

dependencies {
    api(project(":platform-api"))
}

tasks.withType<JavaCompile>().configureEach {
    // FFM restricted operations are the intentional purpose of this isolated module.
    options.compilerArgs.add("-Xlint:-restricted")
}
