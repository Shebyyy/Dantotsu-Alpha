dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        flatDir { dirs("app/libs") }
    }
}
rootProject.name = "Dantotsu"
include(":app")
