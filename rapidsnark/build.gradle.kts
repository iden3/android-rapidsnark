import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish") version "0.29.0"
    signing
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "io.iden3.rapidsnark"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                cppFlags += "-O2 -frtti -fexceptions -Wall -fstack-protector-all"
                abiFilters += listOf("x86_64", "arm64-v8a")
            }
        }
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("x86_64", "arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/java")
            jniLibs.srcDir("src/main/libs")
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
}

mavenPublishing {
    coordinates("io.iden3", "rapidsnark", "0.0.1-alpha.1")

    pom {
        name.set("rapidsnark")
        description.set("This library is Android Kotlin wrapper for the [Rapidsnark](https://github.com/iden3/rapidsnark). It enables the generation of proofs for specified circuits within an Android environment.")
        inceptionYear.set("2024")
        url.set("https://github.com/iden3/android-rapidsnark")
        licenses {
            license {
                name.set("GNU General Public License v3.0")
                url.set("https://github.com/iden3/android-rapidsnark/blob/main/COPYING")
                distribution.set("https://github.com/iden3/android-rapidsnark/blob/main/COPYING")
            }
        }
        developers {
            developer {
                id.set("demonsh")
                name.set("Dmytro Sukhyi")
                url.set("https://github.com/demonsh/")
            }
            developer {
                id.set("5eeman")
                name.set("Yaroslav Moria")
                url.set("https://github.com/5eeman/")
            }
        }
        scm {
            url.set("https://github.com/iden3/android-rapidsnark/")
            connection.set("scm:git:git://github.com/iden3/android-rapidsnark.git")
            developerConnection.set("scm:git:ssh://git@github.com/iden3/android-rapidsnark.git")
        }
    }

    publishToMavenCentral(SonatypeHost.DEFAULT)

    signAllPublications()
}