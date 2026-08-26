plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import java.util.Properties

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.avitoohband.nutrun"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.avitoohband.nutrun"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val mapsApiKey = providers.gradleProperty("MAPS_API_KEY").orNull
        val backendBaseUrl = providers.gradleProperty("BACKEND_BASE_URL").orNull
        val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orNull
        val admobBannerId = providers.gradleProperty("ADMOB_BANNER_ID").orNull
        val productionAdsConfigured = !admobAppId.isNullOrBlank() && !admobBannerId.isNullOrBlank()
        val privacyPolicyUrl = providers.gradleProperty("PRIVACY_POLICY_URL").orNull
        val termsOfServiceUrl = providers.gradleProperty("TERMS_OF_SERVICE_URL").orNull
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${privacyPolicyUrl ?: ""}\"")
        buildConfigField("String", "TERMS_OF_SERVICE_URL", "\"${termsOfServiceUrl ?: ""}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${backendBaseUrl ?: ""}\"")
        buildConfigField("boolean", "MAPS_CONFIGURED", (mapsApiKey != null).toString())
        buildConfigField("boolean", "PRODUCTION_ADS_CONFIGURED", productionAdsConfigured.toString())
        buildConfigField(
            "String",
            "ADMOB_BANNER_ID",
            "\"${admobBannerId ?: "ca-app-pub-3940256099942544/6300978111"}\""
        )
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey ?: "REPLACE_WITH_RESTRICTED_MAPS_KEY"
        manifestPlaceholders["ADMOB_APP_ID"] =
            admobAppId ?: "ca-app-pub-3940256099942544~3347511713"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.health.connect.client)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}

android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
}

val releaseGradleProperties = listOf(
    "MAPS_API_KEY",
    "BACKEND_BASE_URL",
    "ADMOB_APP_ID",
    "ADMOB_BANNER_ID",
    "PRIVACY_POLICY_URL",
    "TERMS_OF_SERVICE_URL"
)

val googleTestAdMobPrefix = "ca-app-pub-3940256099942544"

tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    doFirst {
        check(keystorePropertiesFile.exists()) {
            "Release builds require local keystore.properties."
        }
        val missing = releaseGradleProperties.filter { name ->
            providers.gradleProperty(name).orNull.isNullOrBlank()
        }
        check(missing.isEmpty()) {
            "Release builds require Gradle properties: ${missing.joinToString()}. " +
                "Set them in gradle.properties or ~/.gradle/gradle.properties. " +
                "See docs/setup/production-services.md."
        }
        val mapsKey = providers.gradleProperty("MAPS_API_KEY").orNull
        check(mapsKey != "REPLACE_WITH_RESTRICTED_MAPS_KEY") {
            "Release builds require a restricted Maps API key."
        }
        val backendUrl = providers.gradleProperty("BACKEND_BASE_URL").orNull.orEmpty()
        check(backendUrl.startsWith("https://")) {
            "BACKEND_BASE_URL must use HTTPS."
        }
        listOf("PRIVACY_POLICY_URL", "TERMS_OF_SERVICE_URL").forEach { propertyName ->
            val url = providers.gradleProperty(propertyName).orNull.orEmpty()
            check(url.startsWith("https://")) {
                "$propertyName must use HTTPS."
            }
        }
        val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orNull.orEmpty()
        val admobBannerId = providers.gradleProperty("ADMOB_BANNER_ID").orNull.orEmpty()
        check(!admobAppId.startsWith(googleTestAdMobPrefix)) {
            "ADMOB_APP_ID must be a production app id."
        }
        check(!admobBannerId.startsWith(googleTestAdMobPrefix)) {
            "ADMOB_BANNER_ID must be a production banner id."
        }
    }
}
