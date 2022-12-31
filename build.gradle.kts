import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val http4kVersion: String by project
val junitVersion: String by project
val junitLauncherVersion: String by project
val pesticideVersion: String by project
val striktVersion: String by project

application {
    mainClass.set("com.zettai.ApplicationKt")
}

plugins {
    kotlin("jvm") version "1.7.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.http4k:http4k-core:${http4kVersion}")
    implementation("org.http4k:http4k-server-jetty:${http4kVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${junitLauncherVersion}")
    testImplementation("org.http4k:http4k-client-jetty:${http4kVersion}")
    testImplementation("com.ubertob.pesticide:pesticide-core:${pesticideVersion}")
    testImplementation("io.strikt:strikt-core:${striktVersion}")

}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
