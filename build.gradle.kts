plugins {
    kotlin("jvm") version "1.9.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    implementation("io.ktor:ktor-server-websockets:2.3.4")
    implementation("io.ktor:ktor-server-html-builder:2.3.4")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("com.example.mockserver.ApplicationKt")
}

tasks.register("runWithInfo") {
    doLast {
        println("\n✅ Server is running!")
        println("🌐 Open your browser to: http://localhost:8081")
        println("🛑 Press Ctrl+C to stop the server\n")
    }
}