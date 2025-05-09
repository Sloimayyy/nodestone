import org.jetbrains.kotlin.gradle.tasks.KotlinCompile



val EXCLUDE_OLD_BACKENDS = true




plugins {
    kotlin("jvm") version "2.0.21"
    `maven-publish`
}

group = "com.github.sloimayyy"
version = "1.1.0"

repositories {
    mavenCentral()
    //mavenLocal()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io/")
}

dependencies {
    implementation(kotlin("stdlib"))
    //implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")


    //implementation("com.github.Querz:NBT:6.1")
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("com.github.sloimayyy:smath:1.1.4")
    implementation("com.github.sloimayyy:mcvolume:1.0.15")

    // lwjgl
    /*if (!CPU_BACKEND_ONLY) {
        implementation(platform("org.lwjgl:lwjgl-bom:3.3.2"))
        implementation("org.lwjgl:lwjgl")
        implementation("org.lwjgl:lwjgl-glfw")
        implementation("org.lwjgl:lwjgl-opengl")
        runtimeOnly("org.lwjgl:lwjgl::natives-windows") // Or linux/macos based on your OS
        runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
        runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    }*/

    //testImplementation(kotlin("test"))

}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.sloimay"
            artifactId = "nodestone"
            version = project.version as String

            from(components["java"])
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
    }

    sourceSets {
        main {
            kotlin {
                if (EXCLUDE_OLD_BACKENDS) {
                    exclude("nodestonecore/simulation/backends/gpubackend/**")
                    exclude("nodestonecore/simulation/backends/mamba/**")
                    exclude("nodestonecore/simulation/backends/ripper/**")
                    //exclude("nodestonecore/backends/shrimple/**")
                }
            }
        }
    }
}

