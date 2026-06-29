import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

val libraryGroupId = providers.gradleProperty("GROUP").get()
val libraryVersion = providers.gradleProperty("VERSION_NAME").get()

group = libraryGroupId
version = libraryVersion

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FlowEntity"
            isStatic = true
        }
    }
    
    androidLibrary {
        namespace = "com.speakerbox.flowentity"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }
    
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set(providers.gradleProperty("POM_NAME"))
        description.set(providers.gradleProperty("POM_DESCRIPTION"))
        inceptionYear.set(providers.gradleProperty("POM_INCEPTION_YEAR"))
        url.set(providers.gradleProperty("POM_URL"))

        licenses {
            license {
                name.set(providers.gradleProperty("MAVEN_LICENSE_NAME"))
                url.set(providers.gradleProperty("MAVEN_LICENSE_URL"))
                distribution.set(providers.gradleProperty("MAVEN_LICENSE_DIST"))
            }
        }

        developers {
            developer {
                id.set(providers.gradleProperty("MAVEN_DEVELOPER_ID"))
                name.set(providers.gradleProperty("MAVEN_DEVELOPER_NAME"))
                email.set(providers.gradleProperty("MAVEN_DEVELOPER_EMAIL"))
                organization.set(providers.gradleProperty("MAVEN_DEVELOPER_ORGANIZATION"))
                organizationUrl.set(providers.gradleProperty("MAVEN_DEVELOPER_ORGANIZATION_URL"))
                url.set(providers.gradleProperty("MAVEN_DEVELOPER_URL"))
            }
        }

        scm {
            url.set(providers.gradleProperty("POM_SCM_URL"))
            connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
            developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
        }
    }
}
