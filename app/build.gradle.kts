import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 제출용 APK 파일명 (형식: 팀명_앱이름_release.apk)
val releaseApkName = "팀명_렌트로그_release.apk"

// release 서명 정보는 git에 올리지 않는 keystore.properties(프로젝트 루트)에서 읽는다
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreFile.exists()
// 값에 실수로 넣은 앞뒤 공백·따옴표는 무시한다 (예: storeFile="D:/keys/x.jks")
fun keystoreProp(name: String): String? =
    keystoreProps.getProperty(name)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")

android {
    namespace = "com.example.pleosrantlog"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleosrantlog"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProp("storeFile")!!)
                storePassword = keystoreProp("storePassword")
                keyAlias = keystoreProp("keyAlias")
                keyPassword = keystoreProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            // R8 최적화는 끈다. packageScope로 kotlin.** 를 최적화하면 Kotlin 표준 라이브러리의
            // 패키지 전용 클래스(CollectionsKt__IterablesKt 등)가 다른 패키지로 옮겨져
            // 시작하자마자 IllegalAccessError로 크래시한다. (debug와 같은 코드로 동작하게 유지)
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// 서명된 release APK를 apk/<releaseApkName>으로 복사한다.
// ./gradlew releaseApk 로 직접 실행하거나, assembleRelease(Generate Signed APK 마법사 포함)가 끝나면 자동 실행된다.
// 서명되지 않은 APK(app-release-unsigned.apk)는 복사하지 않는다.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val apkName = releaseApkName
        tasks.register<Copy>("releaseApk") {
            group = "build"
            description = "서명된 release APK를 apk/$apkName 로 복사합니다."
            from(variant.artifacts.get(SingleArtifact.APK)) {
                include("*.apk")
                exclude("*-unsigned.apk")
            }
            into(rootProject.layout.projectDirectory.dir("apk"))
            rename { apkName }
        }
    }
}
tasks.configureEach {
    if (name == "assembleRelease") finalizedBy("releaseApk")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.ai.pleos.vehicle)
    implementation(libs.ai.pleos.tts)
    implementation(libs.ai.pleos.navi)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}