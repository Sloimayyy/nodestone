import org.jetbrains.kotlin.gradle.tasks.KotlinCompile




val CPU_BACKEND_ONLY = true

val EXCLUDE_OLD_BACKENDS = true




plugins {
    kotlin("jvm") version "1.6.10"
    `maven-publish`
}

group = "com.sloimay"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.github.Querz:NBT:6.1")
    implementation("com.beust:klaxon:5.5")
    implementation("com.sloimay:smath:1.0.0")
    implementation("com.sloimay:mcvolume:1.0.0")

    // lwjgl
    if (!CPU_BACKEND_ONLY) {
        implementation(platform("org.lwjgl:lwjgl-bom:3.3.2"))
        implementation("org.lwjgl:lwjgl")
        implementation("org.lwjgl:lwjgl-glfw")
        implementation("org.lwjgl:lwjgl-opengl")
        runtimeOnly("org.lwjgl:lwjgl::natives-windows") // Or linux/macos based on your OS
        runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
        runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    }

    //testImplementation(kotlin("test"))

}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.sloimay"
            artifactId = "threadstone_core"
            version = "1.0.0"

            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        // Your other Kotlin options here
    }

    sourceSets {
        main {
            kotlin {
                if (CPU_BACKEND_ONLY) {
                    exclude("threadstonecore/backends/gpubackend/**")
                    exclude("threadstonecore/backends/mamba/**")
                }
                if (EXCLUDE_OLD_BACKENDS) {
                    exclude("threadstonecore/backends/gpubackend/**")
                    exclude("threadstonecore/backends/mamba/**")
                    exclude("threadstonecore/backends/ripper/**")
                    //exclude("threadstonecore/backends/shrimple/**")
                }
            }
        }
    }
}

