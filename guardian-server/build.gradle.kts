plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

group = "com.vanish994"
version = "0.1.0"

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

application {
    mainClass = "com.vanish994.cairnsolo.guardian.ServerKt"
}

tasks.test { useJUnitPlatform() }
