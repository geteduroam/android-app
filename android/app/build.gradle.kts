import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    alias(libs.plugins.hilt)
    alias(libs.plugins.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.protobuf)
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
    alias(libs.plugins.play.publisher)
    alias(libs.plugins.compose.compiler)
}

if (JavaVersion.current() < JavaVersion.VERSION_17) {
    throw GradleException("Please use JDK ${JavaVersion.VERSION_17} or above")
}

val buildNumberFile = File("${buildDir}/build_number.txt")
val versionNameFile = File("${projectDir}/version_name.txt")
val appCode = if (buildNumberFile.exists()) {
    buildNumberFile.readText().trim().toInt()
} else {
    1
}
val appName = if (versionNameFile.exists()) {
    versionNameFile.readText() + "($appCode)"
} else {
    "Alpha($appCode)"
}

android {
    compileSdk = libs.versions.android.sdk.compile.get().toInt()
    namespace = "app.eduroam.geteduroam"

    defaultConfig {
        applicationId = "app.eduroam.geteduroam"
        minSdk = libs.versions.android.sdk.min.get().toInt()
        targetSdk = libs.versions.android.sdk.target.get().toInt()
        versionCode = appCode
        versionName = appName
        manifestPlaceholders += mapOf(
            "appAuthRedirectScheme" to "app.eduroam.geteduroam"
        )
        firebaseAppDistribution {
            appId = System.getenv("FIREBASE_APP_ID")
            artifactType = "APK"
            releaseNotesFile = "android/app/build/release_notes.txt"
            groups = "all-testers"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = System.getenv("PRODUCTION_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("PRODUCTION_KEYSTORE_ALIAS")
            keyPassword = System.getenv("PRODUCTION_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions.add("brand")
    flavorDimensions.add("distribution")

    productFlavors {
        create("eduroam") {
            dimension = "brand"
            buildConfigField("String", "OAUTH_CLIENT_ID", "\"app.eduroam.geteduroam\"")
            buildConfigField("String", "OAUTH_REDIRECT_URI", "\"app.eduroam.geteduroam:/\"")
            buildConfigField("String", "DISCOVERY_BASE_URL", "\"https://discovery.eduroam.app/\"")
        }
        create("govroam") {
            dimension = "brand"
            applicationId = "app.govroam.getgovroam"
            buildConfigField("String", "OAUTH_CLIENT_ID", "\"app.govroam.getgovroam\"")
            buildConfigField("String", "OAUTH_REDIRECT_URI", "\"app.govroam.getgovroam:/\"")
            buildConfigField("String", "DISCOVERY_BASE_URL", "\"https://discovery.getgovroam.nl/\"")
            manifestPlaceholders += mapOf(
                "appAuthRedirectScheme" to "app.govroam.getgovroam"
            )
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", "false")
        }
        create("googlePlay") {
            dimension = "distribution"
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

play {
    track = "internal"
}

val googlePlayImplementation by configurations

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.android.material)
    implementation(libs.androidx.compose.navigation3.ui)
    implementation(libs.androidx.compose.navigation3.runtime)
    implementation(libs.androidx.splash.core)
    implementation(libs.timber)

    implementation(libs.androidx.datastore)
    implementation(libs.google.protobuf)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.bottomsheet)
    implementation(libs.appauth)
    implementation(libs.simpleframework.xml.parser)

    implementation(libs.coroutines)
    implementation(libs.kotlin.serialization)

    //OkHttp client
    implementation(libs.okhttp.okhttp)
    implementation(libs.okhttp.logging)

    //Retrofit with kotlinx serialization for API calls
    implementation(libs.retrofit.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)

    //Compose BOM dependencies
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tool.preview)
    implementation(libs.androidx.compose.lifecycle.vm.compose)
    implementation(libs.androidx.compose.lifecycle.vm.navigation3)

    //Compose hiltViewModel
    implementation(libs.androidx.compose.hilt.navigation)

    // Firebase
    val firebaseBom = platform(libs.firebase.bom)
    googlePlayImplementation(firebaseBom)
    googlePlayImplementation(libs.firebase.crashlytics)


    //Hilt dependencies
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    //Compose debug/tool preview
    debugImplementation(libs.androidx.compose.debug.ui.tooling)
    debugImplementation(libs.androidx.compose.debug.ui.test.manifest)
}
