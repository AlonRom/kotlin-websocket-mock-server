pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "websocket-mock-server"

include(":server")
include(":examples:android")