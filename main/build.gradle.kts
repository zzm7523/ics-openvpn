import com.android.build.gradle.api.ApplicationVariant

/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    id("com.android.application")
    id("checkstyle")
    id("kotlin-android")
}

android {
    compileSdkVersion(30)

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(30)
        multiDexEnabled = true
        versionCode = 271
        versionName = "2.1.1"

        ndk {
            abiFilters.add("arm64-v8a")
            /*
            abiFilters.add("armeabi-v7a")
            abiFilters.add("x86_64")
             */
        }

        /*
        externalNativeBuild {
            cmake {
                arguments = listOf("-DANDROID_TOOLCHAIN=clang", "-DANDROID_STL=c++_static")
            }
        }
         */
    }

    /*
    externalNativeBuild {
        cmake {
            setPath(File("${projectDir}/src/main/cpp/CMakeLists.txt"))
        }
    }
     */

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets")
        }

        create("skeleton") {
        }

        create("ui") {
        }

        getByName("debug") {
        }

        getByName("release") {
        }
    }

    signingConfigs {
        create("release") {
            // ~/.gradle/gradle.properties
            val keystoreFile = "D:/open_my_work/ics-openvpn.jks"
            storeFile = keystoreFile.let { file(it) }
            val keystorePassword = "123456"
            storePassword = keystorePassword
            val keystoreAliasPassword = "123456"
            keyPassword = keystoreAliasPassword
            val keystoreAlias = "ics-openvpn"
            keyAlias = keystoreAlias
        }
    }

    lintOptions {
        enable("BackButton", "EasterEgg", "StopShip", "IconExpectedSize", "GradleDynamicVersion", "NewerVersionAvailable")
        warning("ImpliedQuantity", "MissingQuantity")
        disable("MissingTranslation", "UnsafeNativeCodeLocation")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions("implementation")

    productFlavors {
        create("ui") {
            dimension = "implementation"
        }
        create("skeleton") {
            dimension = "implementation"
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_1_8
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    splits {
        abi {
            isEnable = false
            reset()
            include("arm64-v8a" /* "armeabi-v7a", "x86_64" */)
            isUniversalApk = true
        }
    }

    ndkVersion = "21.3.6528147"

}

dependencies {
    // https://maven.google.com/web/index.html
    // https://developer.android.com/jetpack/androidx/releases/core
    val preferenceVersion = "1.1.1"
    val coreVersion = "1.5.0"
    val materialVersion = "1.1.0"
    val fragmentVersion = "1.3.2"

    implementation("androidx.core:core:$coreVersion")
    implementation("androidx.core:core-ktx:$coreVersion")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.annotation:annotation:1.2.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.1.0")
    implementation("com.sun.mail:android-mail:1.6.5")
    implementation("com.sun.mail:android-activation:1.6.5")
    implementation("com.squareup.okhttp3:okhttp:4.6.0")
    implementation("org.conscrypt:conscrypt-android:2.4.0")
    implementation("com.madgag.spongycastle:bcpkix-jdk15on:1.58.0.0")
    // 不要用 commons-io:commons-io:2.6+, 报下面错误
    // java.lang.NoSuchMethodError: No virtual method toPath()Ljava/nio/file/Path; in class Ljava/io/File; ...
    // !! whole package java.nio is not supported by Android below api 26
    implementation("commons-io:commons-io:2.4")

    implementation(fileTree("E:/keynes/ics-openvpn/libs"))

    implementation(project(":permission"))

    // Is there a nicer way to do this?
    dependencies.add("uiImplementation", "androidx.constraintlayout:constraintlayout:1.1.3")
    dependencies.add("uiImplementation", "androidx.cardview:cardview:1.0.0")
    dependencies.add("uiImplementation", "androidx.recyclerview:recyclerview:1.0.0")
    dependencies.add("uiImplementation", "androidx.appcompat:appcompat:1.1.0")
    dependencies.add("uiImplementation", "androidx.drawerlayout:drawerlayout:1.0.0")
    dependencies.add("uiImplementation", "androidx.fragment:fragment-ktx:$fragmentVersion")
    dependencies.add("uiImplementation", "androidx.preference:preference:$preferenceVersion")
    dependencies.add("uiImplementation", "androidx.preference:preference-ktx:$preferenceVersion")
    dependencies.add("uiImplementation", "androidx.webkit:webkit:1.2.0")

    dependencies.add("uiImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.71")
    dependencies.add("uiImplementation", "org.jetbrains.anko:anko-commons:0.10.4")
    dependencies.add("uiImplementation", "com.github.PhilJay:MPAndroidChart:v3.1.0")
    dependencies.add("uiImplementation", "com.google.android.material:material:$materialVersion")
    dependencies.add("uiImplementation", "androidx.lifecycle:lifecycle-viewmodel-ktx:2.3.1")
    dependencies.add("uiImplementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")

    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.71")
    testImplementation("junit:junit:4.13.1")
    testImplementation("org.mockito:mockito-core:3.5.15")
    testImplementation("org.robolectric:robolectric:4.4")
    testImplementation("androidx.test:core:1.3.0")
}