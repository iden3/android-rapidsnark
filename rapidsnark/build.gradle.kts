import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val githubProperties = Properties()
githubProperties.load(FileInputStream(rootProject.file("github.properties"))) //Set env variable GPR_USER & GPR_API_KEY if not adding a properties file

repositories {
    google()
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/iden3/android-rapidsnark")
        credentials {
            username = githubProperties.getProperty("gpr.user")
            password = githubProperties.getProperty("gpr.key")
        }
    }
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.iden3"
            artifactId = "rapidsnark"
            version = "0.0.1-alpha.2"
            artifact("$buildDir/outputs/aar/rapidsnark-release.aar")
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/iden3/android-rapidsnark")
            credentials {
                username = githubProperties.getProperty("gpr.user")
                password = githubProperties.getProperty("gpr.key")
            }
        }
    }
}
