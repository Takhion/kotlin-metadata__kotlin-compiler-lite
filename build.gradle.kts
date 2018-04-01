import com.jfrog.bintray.gradle.Artifact
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayExtension.GpgConfig
import com.jfrog.bintray.gradle.BintrayExtension.MavenCentralSyncConfig
import com.jfrog.bintray.gradle.BintrayExtension.PackageConfig
import com.jfrog.bintray.gradle.BintrayExtension.VersionConfig
import com.jfrog.bintray.gradle.RecordingCopyTask
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.util.Properties
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val superRootDir: File by extra(rootDir)
project.apply { from(superRootDir.resolve("consts.gradle.kts")) }
val consts: Properties by extra

val gitHubUser: String by consts
val gitHubRepo: String by consts

val gitTag: String by consts
val gitRepo: String by consts

val mainRepoUrl: String by consts

val libName: String by consts
val libDescription: String by consts
val libUrl: String by consts

val libVersion: String by consts

val licenseName: String by consts

val issuesUrl: String by consts

val kotlinDir: File by consts
val outputDir: File by consts
val initFile: File by consts

val bintrayPublish by propertyOrElse(true)
val bintrayOverride by propertyOrElse(false)
val bintrayDryRun by propertyOrElse(false)
val bintrayMavenCentralSync by propertyOrElse(true)
val bintrayMavenCentralClose by propertyOrElse(true)
val bintrayGpgSign by propertyOrElse(true)

val bintrayRepo = "kotlin-metadata"
val bintrayTags = arrayOf("kotlin", "kotlin-metadata")

val bintrayUser: String? = System.getenv("BINTRAY_USER")
val bintrayKey: String? = System.getenv("BINTRAY_KEY")

val sonatypeUser: String? = System.getenv("SONATYPE_USER")
val sonatypePassword: String? = System.getenv("SONATYPE_PASSWORD")

// set up external ant dependencies
val updateDependencies by tasks.creating
kotlinDir.resolve("update_dependencies.xml").let { dependenciesFile ->
    if (dependenciesFile.exists()) {
        ant.importBuild(dependenciesFile)
        updateDependencies.dependsOn(ant.project.defaultTarget)
    }
    else {
        updateDependencies.doFirst { error("Dependencies file not found: '$dependenciesFile'") }
    }
}

// execute inner build

val publish by tasks.creating(GradleBuild::class) {
    dir = kotlinDir
    startParameter.apply {
        showStacktrace = ShowStacktrace.ALWAYS_FULL
        addInitScript(initFile)

    }
    outputs.dir(outputDir)

    doFirst {
        // TODO remove once kotlin.init.gradle is rewritten in Kotlin
        // persist 'consts' properties since groovy files can't apply .kts scripts
        val storeableConsts = Properties()
        for ((key, value) in consts) {
            storeableConsts[key] = when {

                key == "injectedModuleNames" -> @Suppress("UNCHECKED_CAST") (value as List<String>).joinToString(":")
                value is File -> value.canonicalPath
                else -> value
            }
        }
        FileOutputStream(superRootDir.resolve("build/consts.xml"))
            .use { storeableConsts.storeToXML(it, null) }
    }
}

plugins { id("com.jfrog.bintray") version "1.8.0" }

configure<BintrayExtension> {
    publish = bintrayPublish
    override = bintrayOverride
    dryRun = bintrayDryRun
    user = bintrayUser
    key = bintrayKey
    filesSpec {
        fileUploads = fileTree(outputDir).map {
            Artifact().apply {
                file = it
                setPath(it.toRelativeString(outputDir))
            }
        }
    }
    pkg {
        repo = bintrayRepo
        name = libName
        desc = libDescription
        websiteUrl = libUrl
        issueTrackerUrl = issuesUrl
        githubRepo = "$gitHubUser/$gitHubRepo"
        vcsUrl = "https://$gitRepo"
        setLabels(*bintrayTags)
        setLicenses(licenseName)
        version {
            name = libVersion
            vcsTag = gitTag
            gpg.sign = bintrayGpgSign
            mavenCentralSync {
                sync = bintrayMavenCentralSync
                close = if (bintrayMavenCentralClose) "1" else "0"
                user = sonatypeUser
                password = sonatypePassword
            }
        }
    }
}

val upload by tasks.creating {
    dependsOn("bintrayUpload")
}

defaultTasks(updateDependencies, publish)

//region utils
fun propertyOrElse(defaultValue: Boolean) =
    object : ReadOnlyProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) =
            findProperty(property.name)
                .let {
                    when {
                        it is Boolean -> it
                        it is String && it.equals("true", ignoreCase = true) -> true
                        else -> null
                    }
                }
                ?: defaultValue
    }

fun BintrayExtension.filesSpec(configure: RecordingCopyTask.() -> Unit) = filesSpec(closureOf(configure))
fun BintrayExtension.pkg(configure: PackageConfig.() -> Unit) = pkg(closureOf(configure))
fun PackageConfig.version(configure: VersionConfig.() -> Unit) = version(closureOf(configure))
fun VersionConfig.mavenCentralSync(configure: MavenCentralSyncConfig.() -> Unit) = mavenCentralSync(closureOf(configure))
//endregion
