plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

android {
    namespace = "com.speedtest.sdk.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}

// ----- Fastlink SpeedTest publishing -----
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId    = providers.gradleProperty("FASTLINK_GROUP").get()
                artifactId = "fastlink-speedtest-core"
                version    = providers.gradleProperty("FASTLINK_VERSION").get()

                pom {
                    name.set("Fastlink SpeedTest Core")
                    description.set("Fastlink SpeedTest — headless speed-test engine for Android.")
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
