plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization")
}

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-websockets:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("io.ktor:ktor-server-html-builder:2.3.4")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("com.websocketmockserver.ApplicationKt")
}

// Add wrapper task to fix Android Studio wrapper task error
tasks.register("wrapper") {
    doLast {
        println("Wrapper task called on server module - this is expected in multi-module projects")
    }
} 