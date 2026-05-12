pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VleVpn"
include(":runtime-contract")
include(":app")

val enableOlcRtcRuntime = providers.gradleProperty("enableOlcRtcRuntime")
    .orElse(providers.gradleProperty("enableOlcRtc"))
    .orElse("false")
    .get()
    .toBoolean()

if (enableOlcRtcRuntime) {
    include(":runtime-olcrtc")
}
