import org.gradle.api.provider.Provider
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val appVersionCode = 29
val appVersionName = "0.1.29"
val enableOlcRtcRuntime = providers.gradleProperty("enableOlcRtcRuntime")
    .orElse(providers.gradleProperty("enableOlcRtc"))
    .orElse("true")
    .get()
    .toBoolean()
fun Provider<String>.escapedBuildConfigString(): String = get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
fun configValue(name: String): Provider<String> =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name))

val productionApiBaseUrl = "https://umbra-proxy.com/api"
val providerCheckUrl = configValue("VLE_PARTNER_CHECK_URL")
    .orElse(configValue("VLE_PROVIDER_CHECK_URL"))
    .orElse("$productionApiBaseUrl/v1/partner/check")
    .escapedBuildConfigString()
val telemetryUrl = configValue("VLE_TELEMETRY_URL")
    .orElse("$productionApiBaseUrl/v1/mobile/telemetry")
    .escapedBuildConfigString()
val brandingUrl = configValue("VLE_BRANDING_URL")
    .orElse("$productionApiBaseUrl/v1/mobile/branding")
    .escapedBuildConfigString()
val telemetryAppKey = configValue("VLE_TELEMETRY_APP_KEY")
    .orElse("")
    .escapedBuildConfigString()
val telemetryEnabled = configValue("VLE_TELEMETRY_ENABLED")
    .orElse("false")
    .get()
    .toBooleanStrictOrNull() ?: false
val pushRegisterUrl = configValue("VLE_PUSH_REGISTER_URL")
    .orElse("$productionApiBaseUrl/v1/mobile/push/register")
    .escapedBuildConfigString()
val pushEnabled = configValue("VLE_PUSH_ENABLED")
    .orElse("false")
    .get()
    .toBooleanStrictOrNull() ?: false
val inAppPushRegisterUrl = configValue("VLE_IN_APP_PUSH_REGISTER_URL")
    .orElse("$productionApiBaseUrl/v1/mobile/push/in-app/register")
    .escapedBuildConfigString()
val inAppPushInboxUrl = configValue("VLE_IN_APP_PUSH_INBOX_URL")
    .orElse("$productionApiBaseUrl/v1/mobile/push/in-app/inbox")
    .escapedBuildConfigString()
val inAppPushAckUrl = configValue("VLE_IN_APP_PUSH_ACK_URL")
    .orElse("$productionApiBaseUrl/v1/mobile/push/in-app/ack")
    .escapedBuildConfigString()
val inAppPushEnabled = configValue("VLE_IN_APP_PUSH_ENABLED")
    .orElse("true")
    .get()
    .toBooleanStrictOrNull() ?: true
val releaseStoreFilePath = configValue("VLE_RELEASE_STORE_FILE").orNull
val releaseStorePassword = configValue("VLE_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = configValue("VLE_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = configValue("VLE_RELEASE_KEY_PASSWORD").orNull
val releaseSigningEnabled = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "ru.myit.vlevpn"
    compileSdk = 35
    ndkVersion = "28.0.13004108"

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.proxy.umbra"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        setProperty("archivesBaseName", "umbra-vpn-v$appVersionName")
        buildConfigField("String", "PROVIDER_CHECK_URL", "\"$providerCheckUrl\"")
        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
        buildConfigField("String", "BRANDING_URL", "\"$brandingUrl\"")
        buildConfigField("String", "TELEMETRY_APP_KEY", "\"$telemetryAppKey\"")
        buildConfigField("boolean", "TELEMETRY_ENABLED", telemetryEnabled.toString())
        buildConfigField("String", "PUSH_REGISTER_URL", "\"$pushRegisterUrl\"")
        buildConfigField("boolean", "PUSH_ENABLED", pushEnabled.toString())
        buildConfigField("String", "IN_APP_PUSH_REGISTER_URL", "\"$inAppPushRegisterUrl\"")
        buildConfigField("String", "IN_APP_PUSH_INBOX_URL", "\"$inAppPushInboxUrl\"")
        buildConfigField("String", "IN_APP_PUSH_ACK_URL", "\"$inAppPushAckUrl\"")
        buildConfigField("boolean", "IN_APP_PUSH_ENABLED", inAppPushEnabled.toString())
        buildConfigField("boolean", "OLCRTC_RUNTIME_INCLUDED", enableOlcRtcRuntime.toString())
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Release signing is not configured. Set VLE_RELEASE_* Gradle properties or environment variables.")
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":runtime-contract"))
    if (enableOlcRtcRuntime) {
        implementation(project(":runtime-olcrtc"))
        implementation(files("../runtime-olcrtc/libs/olcrtc.aar"))
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(files("libs/libXray.aar"))

    testImplementation(libs.junit)
}

tasks.register("runDebugOnConnectedDevice") {
    group = "install"
    description = "Installs the debug APK and launches it on the first connected Android device."
    dependsOn("installDebug")

    doLast {
        val adb = android.sdkDirectory.resolve("platform-tools/adb").absolutePath
        val devicesOutput = providers.exec {
            commandLine(adb, "devices")
        }.standardOutput.asText.get()

        val serial = devicesOutput
            .lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                parts.takeIf { columns -> columns.size == 2 && columns[1] == "device" }?.first()
            }
            .firstOrNull()
            ?: throw GradleException("No connected Android device or emulator found.")

        providers.exec {
            commandLine(
                adb,
                "-s",
                serial,
                "shell",
                "am",
                "start",
                "-n",
                "com.proxy.umbra/ru.myit.vlevpn.MainActivity",
            )
        }.result.get().assertNormalExitValue()
    }
}

fun registerRuntimePackagingVerification(variantName: String) {
    val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
    val verifyTask = tasks.register("verify${capitalizedVariant}RuntimePackaging") {
        group = "verification"
        description = "Verifies that $variantName APK contains the expected VPN runtime native libraries."

        doLast {
            val apkDir = layout.buildDirectory.dir("outputs/apk/$variantName").get().asFile
            val apk = apkDir
                .listFiles { file -> file.extension == "apk" }
                ?.singleOrNull()
                ?: throw GradleException("Expected exactly one $variantName APK in ${apkDir.absolutePath}.")

            val expectedNativeLibs = buildList {
                add("lib/arm64-v8a/libgojni.so")
                if (enableOlcRtcRuntime) {
                    add("lib/arm64-v8a/libolcrtcjni.so")
                    add("lib/arm64-v8a/libhev-socks5-tunnel.so")
                    add("lib/arm64-v8a/libvle_olcrtc_tun2socks.so")
                }
            }

            ZipFile(apk).use { zip ->
                val missing = expectedNativeLibs.filter { entry -> zip.getEntry(entry) == null }
                if (missing.isNotEmpty()) {
                    throw GradleException(
                        "Runtime packaging check failed for ${apk.name}. Missing native libs: ${missing.joinToString()}. " +
                            "Run a clean build and keep enableOlcRtcRuntime=true for production/test APKs.",
                    )
                }
            }

            val buildConfig = layout.buildDirectory
                .file("generated/source/buildConfig/$variantName/ru/myit/vlevpn/BuildConfig.java")
                .get()
                .asFile
            val expectedBuildConfigValue = "OLCRTC_RUNTIME_INCLUDED = $enableOlcRtcRuntime"
            if (!buildConfig.readText().contains(expectedBuildConfigValue)) {
                throw GradleException(
                    "Runtime packaging check failed for ${apk.name}. BuildConfig does not contain " +
                        "$expectedBuildConfigValue. Run a clean build.",
                )
            }
        }
    }

    tasks.named("assemble$capitalizedVariant") {
        finalizedBy(verifyTask)
    }
}

afterEvaluate {
    registerRuntimePackagingVerification("debug")
    registerRuntimePackagingVerification("release")
}
