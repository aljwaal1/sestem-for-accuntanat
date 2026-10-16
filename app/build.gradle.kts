plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.explapp.accountinglocal"; compileSdk = 35
  defaultConfig { applicationId = "com.explapp.accountinglocal"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.3.1-android-alpha" }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
