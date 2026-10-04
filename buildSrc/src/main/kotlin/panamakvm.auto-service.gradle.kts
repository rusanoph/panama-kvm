import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile

val libraries = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

plugins.withId("java-library") {
    dependencies.add("compileOnly", libraries.findLibrary("auto-service-annotations").get())
    dependencies.add("annotationProcessor", libraries.findLibrary("auto-service-processor").get())

    tasks.withType(JavaCompile::class.java).configureEach {
        // AutoService writes the descriptor but does not claim @AutoService on javac 25.
        options.compilerArgs.add("-Xlint:-processing")
    }
}
