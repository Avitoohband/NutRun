plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.avitoohband.nutrun"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.avitoohband.nutrun"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val mapsApiKey = providers.gradleProperty("MAPS_API_KEY").orNull
        val admobAppId = providers.gradleProperty("ADMOB_APP_ID").orNull
        val admobBannerId = providers.gradleProperty("ADMOB_BANNER_ID").orNull
        buildConfigField("String", "BACKEND_BASE_URL", "\"${providers.gradleProperty("BACKEND_BASE_URL").orNull ?: ""}\"")
        buildConfigField("boolean", "MAPS_CONFIGURED", (mapsApiKey != null).toString())
        buildConfigField(
            "String",
            "ADMOB_BANNER_ID",
            "\"${admobBannerId ?: "ca-app-pub-3940256099942544/6300978111"}\""
        )
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey ?: "REPLACE_WITH_RESTRICTED_MAPS_KEY"
        manifestPlaceholders["ADMOB_APP_ID"] =
            admobAppId ?: "ca-app-pub-3940256099942544~3347511713"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
