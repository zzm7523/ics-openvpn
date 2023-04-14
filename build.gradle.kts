/*
 * Copyright (c) 2012-2019 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */


buildscript {
    repositories {
//      mavenCentral()
        maven(url = "https://maven.aliyun.com/nexus/content/groups/public/")
//      google()
        maven(url = "https://maven.aliyun.com/nexus/content/repositories/google")
//      jcenter()
        maven(url = "https://maven.aliyun.com/nexus/content/repositories/jcenter")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
    }
}


allprojects {
    repositories {
//      mavenCentral()
        maven(url = "https://maven.aliyun.com/nexus/content/groups/public/")
//      google()
        maven(url = "https://maven.aliyun.com/nexus/content/repositories/google")
//      jcenter()
        maven(url = "https://maven.aliyun.com/nexus/content/repositories/jcenter")
        maven(url = "https://jitpack.io")
    }
}
