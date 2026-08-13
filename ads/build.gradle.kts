plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.magic.ads"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // api, not implementation: NativeAdViewBinder's public signature exposes AdMob's
    // NativeAd/NativeAdView directly (Tier 2 custom native layouts need the real types), so
    // consumers must see this dependency on their own compile classpath too.
    api("com.google.android.gms:play-services-ads:24.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")

    implementation("com.applovin:applovin-sdk:13.5.1")
    implementation("com.applovin.mediation:google-adapter:24.7.0.0")

    // Shimmer loading placeholder for NativeAdManager.showLoading() — no external asset files
    // needed (unlike Lottie), just a plain skeleton layout the library draws itself.
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
