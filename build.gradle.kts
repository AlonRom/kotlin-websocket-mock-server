plugins {
    kotlin("jvm") version "1.9.0" apply false
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    kotlin("plugin.serialization") version "1.9.0" apply false
    application
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

// Server module configuration
subprojects {
    if (name == "server") {
        apply(plugin = "kotlin")
        apply(plugin = "application")
        
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
    }
}

tasks.register("runWithInfo") {
    doLast {
        println("\n✅ Server is running!")
        println("🌐 Open your browser to: http://localhost:8081")
        println("🛑 Press Ctrl+C to stop the server\n")
    }
}