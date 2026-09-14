import org.gradle.api.DefaultTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

val libraryGroupId = providers.gradleProperty("GROUP").get()
val libraryVersion = providers.gradleProperty("VERSION_NAME").get()
val libraryArtifactId = providers.gradleProperty("POM_ARTIFACT_ID").get()

group = libraryGroupId
version = libraryVersion

abstract class UpdateSwiftPackageTask : DefaultTask()
{
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty

    @get:Input
    abstract val version: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val packageFile: RegularFileProperty

    @TaskAction
    fun update()
    {
        val archiveFile = archive.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")

        archiveFile.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)

            while (read > 0)
            {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }

        val checksum = digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        val packageFileValue = packageFile.get().asFile
        val packageText = packageFileValue.readText()
        val versionPattern = Regex("""(?m)^let flowEntityVersion = "[^"]*"$""")
        val checksumPattern = Regex("""(?m)^let flowEntityChecksum = "[^"]*"$""")

        require(versionPattern.containsMatchIn(packageText)) {
            "FlowEntity version was not found in ${packageFileValue.path}"
        }
        require(checksumPattern.containsMatchIn(packageText)) {
            "FlowEntity checksum was not found in ${packageFileValue.path}"
        }

        val updatedPackageText = packageText
            .replace(versionPattern, "let flowEntityVersion = \"${version.get()}\"")
            .replace(checksumPattern, "let flowEntityChecksum = \"$checksum\"")

        packageFileValue.writeText(updatedPackageText)
    }
}

kotlin {
    val xcf = XCFramework("FlowEntity")

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
            xcf.add(this)
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

val assembleReleaseXCFramework =
    tasks.named("assembleFlowEntityReleaseXCFramework")

val zipReleaseXCFramework = tasks.register<Zip>("zipFlowEntityXCFramework")
{
    dependsOn(assembleReleaseXCFramework)

    from(layout.buildDirectory.dir("XCFrameworks/release"))
    include("FlowEntity.xcframework/**")

    archiveBaseName.set(libraryArtifactId)
    archiveVersion.set(libraryVersion)
    archiveClassifier.set("ios-xcframework")
    destinationDirectory.set(layout.buildDirectory.dir("publish"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val updateSwiftPackage = tasks.register<UpdateSwiftPackageTask>("updateSwiftPackage")
{
    dependsOn(zipReleaseXCFramework)

    archive.set(zipReleaseXCFramework.flatMap { it.archiveFile })
    version.set(libraryVersion)
    packageFile.set(layout.projectDirectory.file("../Package.swift"))
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

extensions.configure<PublishingExtension>
{
    publications
        .withType<MavenPublication>()
        .configureEach {
            if (name == "kotlinMultiplatform")
            {
                artifact(zipReleaseXCFramework)
            }
        }
}

listOf(
    "publishToMavenLocal",
    "publishToMavenCentral",
    "publishAndReleaseToMavenCentral"
).forEach { taskName ->
    tasks.named(taskName)
    {
        dependsOn(updateSwiftPackage)
    }
}
