plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    implementation("io.ktor:ktor-server-websockets:2.3.4")
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