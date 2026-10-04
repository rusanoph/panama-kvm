import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.Sync
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
}

group = "io.sagittarius"
version = "0.1.0"

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.apply("java-library")
    pluginManager.apply("jacoco")

    extensions.configure<JacocoPluginExtension> {
        toolVersion = libraries.findVersion("jacoco").get().requiredVersion
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        add("testImplementation", platform(libraries.findLibrary("junit-bom").get()))
        add("testImplementation", libraries.findLibrary("junit-jupiter").get())
        add("testRuntimeOnly", libraries.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform {
            if (name != "kvmTest") {
                excludeTags("kvm")
            }
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            charSet = "UTF-8"
            addBooleanOption("Xdoclint:all", true)
            addBooleanOption("Werror", true)
            links("https://docs.oracle.com/en/java/javase/25/docs/api/")
        }
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
        }
        manifest.attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "PanamaKVM contributors",
            "Bundle-License" to "MIT"
        )
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.named("test"))
        reports {
            xml.required = true
            html.required = true
            csv.required = false
        }
    }
}

tasks.register("coverage") {
    group = "verification"
    description = "Runs portable tests and generates JaCoCo XML and HTML reports."
    dependsOn(subprojects.map { "${it.path}:jacocoTestReport" })
}

tasks.register("kvmTest") {
    group = "verification"
    description = "Runs tests tagged 'kvm'; requires Linux x86-64 and /dev/kvm."
    dependsOn(":virtualization-kvm:kvmTest")
}

val releaseVersion = version.toString()
val cliDistributionDirectory = layout.projectDirectory.dir("cli/build/distributions")

tasks.register<Sync>("releaseArtifacts") {
    group = "distribution"
    description = "Builds the verified public CLI distributions in build/release."

    dependsOn(
        subprojects.flatMap { project ->
            listOf("${project.path}:build", "${project.path}:javadoc")
        } + listOf(":cli:distTar", ":cli:distZip")
    )

    from(cliDistributionDirectory.file("panama-kvm-$releaseVersion.tar")) {
        rename(
            "panama-kvm-$releaseVersion\\.tar",
            "panama-kvm-$releaseVersion-linux-x86_64.tar"
        )
    }
    from(cliDistributionDirectory.file("panama-kvm-$releaseVersion.zip")) {
        rename(
            "panama-kvm-$releaseVersion\\.zip",
            "panama-kvm-$releaseVersion-windows-x86_64.zip"
        )
    }
    into(layout.buildDirectory.dir("release"))
}
