plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    `maven-publish`
    signing
}

android {
    namespace = "com.speedtest.sdk.ui"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    api(project(":fastlink-speedtest-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}

// ----- Fastlink SpeedTest publishing -----
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId    = providers.gradleProperty("FASTLINK_GROUP").get()
                artifactId = "fastlink-speedtest-ui"
                version    = providers.gradleProperty("FASTLINK_VERSION").get()

                pom {
                    name.set("Fastlink SpeedTest UI")
                    description.set("Fastlink SpeedTest — drop-in Jetpack Compose UI for the Fastlink speed-test engine.")
                    url.set(providers.gradleProperty("FASTLINK_URL").get())
                    licenses {
                        license {
                            name.set(providers.gradleProperty("FASTLINK_LICENSE_NAME").get())
                            url.set(providers.gradleProperty("FASTLINK_LICENSE_URL").get())
                        }
                    }
                    developers {
                        developer {
                            id.set(providers.gradleProperty("FASTLINK_DEV_ID").get())
                            name.set(providers.gradleProperty("FASTLINK_DEV_NAME").get())
                        }
                    }
                    scm {
                        url.set(providers.gradleProperty("FASTLINK_URL").get())
                        connection.set(providers.gradleProperty("FASTLINK_SCM").get())
                        developerConnection.set(providers.gradleProperty("FASTLINK_SCM").get())
                    }
                }
            }
        }
        repositories {
            maven {
                name = "OSSRH"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = providers.gradleProperty("ossrhUsername").orNull
                    password = providers.gradleProperty("ossrhPassword").orNull
                }
            }
        }
    }

    signing {
        // Only sign when a key is configured (skips local builds without GPG).
        isRequired = providers.gradleProperty("signing.keyId").isPresent
        sign(publishing.publications["release"])
    }
}
