// main build file: designed to be resilient to changes, but fails fast if anything major changed

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.publish.maven.MavenPom
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.resolver.kotlinBuildScriptModelTarget
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties

//region constants
val superRootDir: File by extra(rootDir.parentFile)
project.apply { from(superRootDir.resolve("consts.gradle.kts")) }
val consts: Properties by extra

val codeVersion: String by consts
val kotlinVersion: String by consts

val gitTag: String by consts
val gitRepo: String by consts

val taggedRepoUrl: String by consts

val libShortName: String by consts
val libLongName: String by consts
val libDescription: String by consts
val libUrl: String by consts

val libGroupId: String by consts
val libArtifactId: String by consts
val libVersion: String by consts

val libPackage: String by consts

val publicationName: String by consts

val authorName: String by consts
val authorEmail: String by consts
val authorUrl: String by consts

val licenseName: String by consts
val licenseUrl: String by consts

val issuesSystem: String by consts
val issuesUrl: String by consts

val outputDir: File by consts

val projectEmpty: String by consts

val projectReflect: String by consts
val projectReflectApi: String by consts
val projectBuildCommon: String by consts
val projectDeserialization: String by consts
val projectDescriptorsRuntime: String by consts

val srcCore = "$rootDir/core"
val srcReflectApi = "$srcCore/reflection.jvm/src"
val srcDescriptorsRuntime = "$srcCore/descriptors.runtime/src"

val srcDirDeserializationFull = buildDir.resolve("deserialization-protobufs-full")

val taskCopyAnnotations = "copyAnnotations"
val taskReflectShadowJar = "reflectShadowJar"
val taskRelocateCoreSources = "relocateCoreSources"
val taskSourcesJar = "sourcesJar"

val taskPublish = "publish${publicationName.capitalize()}PublicationToMavenRepository"

val packageJetbrains = "org.jetbrains.kotlin"
val packageReflectImpl = "kotlin.reflect.jvm.internal.impl"
val packageLib = libPackage

val packageProtoOriginal = "com.google.protobuf"
val packageProtoJetbrains = "org.jetbrains.kotlin.protobuf"

val packageJavaxInjectOriginal = "javax.inject"
val packageJavaxInjectJetbrains = "org.jetbrains.kotlin.javax.inject"

//region regex
//language=RegExp
val regexMatchProtoPackage = Regex("" +
    "\\s*" +                          // space [optional]
    "package" +                       // 'package' text
    "\\s+" +                          // space
    packageLib.replace(".", "\\.") +  // text (matching package)
    ("(?:" +                          // start group [optional]
        "\\." +                       // - 'dot' char
        ".+" +                        // - any text (subpackages)
        ")?") +                       // - end optional group
    ";")                              // 'semicolon' char

//language=RegExp
val regexTrimProtoImport = Regex("" +
    ("(?<before>" +                   // start group (' import "')
        "\\s*" +                      // - space [optional]
        "import" +                    // - 'import' text
        "\\s+" +                      // - space
        "\"" +                        // - 'quotes' char (opens)
        ")") +                        // - end group
    "core/deserialization/src/" +     // text (to remove)
    ("(?<after>" +                    // start group ('**.proto"; ')
        ".+" +                        // - any text (proto path)
        "\\." +                       // - 'dot' char
        "proto" +                     // - 'proto' text (extension)
        "\"" +                        // - 'quotes' char (closes)
        ";" +                         // - 'semicolon' char
        "\\s*" +                      // - space [optional]
        ")"))                         // - end group

//language=RegExp
val regexTrimDebugProtoFileName = Regex("" +
    "Debug" +                         // 'Debug' text
    ("(" +                            // start group (file name and extension)
        ".*" +                        // - any text [optional] (name prefix)
        "ProtoBuf" +                  // - 'ProtoBuf' text
        "\\." +                       // - 'dot' char
        ".+" +                        // - any text (file extension)
        ")"))                         // - end group

//language=RegExp
val regexTrimDebugProtoClassName = Regex("" +
    "Debug" +                         // 'Debug' text
    ("(" +                            // start group (class name)
        "[^\\s\\.]*?" +               // - any text except whitespace or 'dot' char [optional, non-greedy] (name prefix)
        "ProtoBuf" +                  // - 'ProtoBuf' text
        ")"))                         // - end group
//endregion
//endregion

evaluationDependsOn(projectReflect)         // for tasks
evaluationDependsOn(projectDeserialization) // for srcSets

val sourceProtobuf by configurations.creating
val sourceJavaxInject by configurations.creating

dependencies {
    sourceProtobuf(commonDep("com.google.protobuf", "protobuf-java") + ":sources")
    sourceJavaxInject(commonDep("javax.inject") + ":sources")
}

val publishJars by tasks.creating {
    dependsOn("$projectReflect:$taskPublish")
    defaultTasks(this)
}

// create a copy of [projectDeserialization] srcDirs, plus: extra protos, full versions of compiled protos, protobuf sources
val copyDeserializationSrcSets by tasks.creating(Copy::class) {
    val mainSrcDirs = project(projectDeserialization)
        .the<JavaPluginConvention>()
        .sourceSets["main"]
        .java
        .srcDirs

    val extraProtos = project(projectBuildCommon)
        .projectDir
        .resolve("src")
        .asFileTree { include("**.proto") }
        .files // flatten

    val compiledProtosFull = project(projectBuildCommon)
        .projectDir
        .resolve("test")
        .asFileTree {
            include { it.name.matches(regexTrimDebugProtoFileName) }
            rename(regexTrimDebugProtoFileName.toPattern(), "$1")
            mapEachLine { it.replace(regexTrimDebugProtoClassName, "$1") }
        }

    val pathProtoOriginal = packageProtoOriginal.packageToPath()
    val pathProtoJetbrains = packageProtoJetbrains.packageToPath()
    val sourcesProtobuf = sourceProtobuf.collect {
        exclude("META-INF/**")
        eachFile { path = path.replace(pathProtoOriginal, pathProtoJetbrains) }
        mapEachLine { it.replace(packageProtoOriginal, packageProtoJetbrains) }
    }

    into(srcDirDeserializationFull)

    from(mainSrcDirs)
    from(extraProtos)
    from(compiledProtosFull) { duplicatesStrategy = INCLUDE }
    from(sourcesProtobuf) { duplicatesStrategy = INCLUDE }
}

// force the enhanced srcDir
with(project(projectDeserialization)) {
    tasks["compileJava"]
        .dependsOn(copyDeserializationSrcSets)
        .doFirst { the<JavaPluginConvention>().sourceSets["main"].java.setSrcDirs(listOf(srcDirDeserializationFull)) }
}

with(project(projectReflect)) {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            // exclude unneeded dependencies
            remove(project(projectReflectApi))
            remove(project(projectDescriptorsRuntime))
        }
    }

    // exclude unneeded sources
    tasks[taskCopyAnnotations].enabled = false

    val classesJar = (tasks[taskReflectShadowJar] as ShadowJar).apply {
        // exclude sources from classes jar
        exclude("**/*.kt", "**/*.java")
        // override classes package relocation
        val getPattern = privateField<SimpleRelocator, String?>("pattern")
        val getShadedPattern = privateField<SimpleRelocator, String?>("shadedPattern")
        val oldRelocators = relocators
        relocators = mutableListOf<Relocator>() // reset relocators
        oldRelocators.forEach {
            // if any relocator is not [SimpleRelocator] or shaded/pattern is `null`, investigate what changed
            it as SimpleRelocator
            val pattern = it.getPattern()!!
            val shadedPattern = it.getShadedPattern()!!.replaceFirst(packageReflectImpl, packageLib)
            relocate(pattern, shadedPattern)
        }
        // make this the main classes jar
        classifier = null
        // edit manifest
        manifest {
            attributes["Implementation-Title"] = libShortName
            attributes["Implementation-Version"] = kotlinVersion
            attributes["Implementation-CodeVersion"] = codeVersion
            attributes["Implementation-Vendor"] = authorName
        }
    }

    (tasks[taskRelocateCoreSources] as Copy).apply {
        val pathReflectImpl = packageReflectImpl.packageToPath()
        val pathLib = packageLib.packageToPath()

        val packageJavaxInjectLib = packageJavaxInjectJetbrains.replaceFirst(packageJetbrains, packageLib)
        val pathJavaxOriginal = packageJavaxInjectOriginal.packageToPath()
        val pathJavaxInjectLib = packageJavaxInjectLib.packageToPath()
        val sourcesJavaxInject = sourceJavaxInject.collect {
            exclude("META-INF/**")
            eachFile { path = path.replace(pathJavaxOriginal, pathJavaxInjectLib) }
            mapEachLine { it.replace(packageJavaxInjectOriginal, packageJavaxInjectLib) }
        }

        from(sourcesJavaxInject)
        from(srcDirDeserializationFull) { duplicatesStrategy = INCLUDE }

        // relocate package paths
        eachFile { path = path.replace(pathReflectImpl, pathLib) }
        // relocate package strings
        filter { it.replace(packageReflectImpl, packageLib) }

        // adjust proto package/imports since they're top level
        filesMatching("**.proto") {
            // remove package
            // TODO file bug: `null` is valid (but it complains) and should remove the line (but it doesn't)
            mapEachLine { it.takeUnless { it.matches(regexMatchProtoPackage) } ?: "" }
            // trim import path
            mapEachLine { it.replace(regexTrimProtoImport, "$1$2") }
        }
    }

    val sourcesJar = (tasks[taskSourcesJar] as Jar).apply {
        // exclude unneeded dependencies
        includeEmptyDirs = false
        excludeDir(srcReflectApi)
        excludeDir(srcDescriptorsRuntime)
    }

    tasks.remove(tasks["publish"])
    apply { plugin("maven-publish") }
    the<PublishingExtension>().apply {
        repositories.maven { url = uri(outputDir) }
        (publications) {
            publicationName(MavenPublication::class) {
                from(components["java"])
                // replace default classes jar, plus add sources jar and (empty) javadoc jar
                setArtifacts(listOf(classesJar, sourcesJar, javadocJar()))

                groupId = libGroupId
                artifactId = libArtifactId
                version = libVersion
                pom.buildXml {
                    "name"..libLongName
                    "description"..libDescription
                    "url"..libUrl
                    "licenses" {
                        "license" {
                            "name"..licenseName
                            "url"..licenseUrl
                        }
                    }
                    "issueManagement" {
                        "system"..issuesSystem
                        "url"..issuesUrl
                    }
                    "developers" {
                        "developer" {
                            "name"..authorName
                            "email"..authorEmail
                            "organization"..authorName
                            "organizationUrl"..authorUrl
                        }
                    }
                    "scm" {
                        "connection".."scm:git:git://$gitRepo"
                        "developerConnection".."scm:git:ssh://$gitRepo"
                        "tag"..gitTag
                        "url"..taggedRepoUrl
                    }
                    "properties" {
                        "codeVersion"..codeVersion
                        "kotlinVersion"..kotlinVersion
                        "gitSubmodulesStatus"..gitSubmoduleStatus()
                    }
                }
            }
        }
    }
}

//region utils
fun File.asFileTree(configure: ConfigurableFileTree.() -> Unit = {}) = fileTree(this, configure)

fun ContentFilterable.mapEachLine(transform: (String) -> String) = filter(transform)

fun String.packageToPath() = replace('.', '/')

fun Configuration.collect(matching: PatternFilterable.() -> Unit = {}): FileTree = this
    .map {
        if (it.isDirectory)
            fileTree(it)
        else
            zipTree(it)
    }
    .reduce { mainTree, nextTree -> mainTree.plus(nextTree) }
    .matching(matching)

fun DependencySubstitutions.remove(dependency: ComponentSelector) = substitute(dependency).with(project(projectEmpty))

inline fun <reified T : Any, R> privateField(fieldName: String): T.() -> R {
    val field = T::class.java.getDeclaredField(fieldName).also { it.isAccessible = true }
    return { @Suppress("UNCHECKED_CAST") (field.get(this) as R) }
}

fun AbstractCopyTask.excludeDir(path: String) {
    val dir = file(path)
    val relativeFilePaths = fileTree(path).asSequence().map { it.toRelativeString(dir) }.asIterable()
    exclude(relativeFilePaths)
}

inline fun MavenPom.buildXml(crossinline xml: NodeContext<Element>.() -> Unit) {
    withXml {
        val root = asElement()
        NodeContext(root, root.ownerDocument).xml()
    }
}

class NodeContext<out T : Node>(val node: T, val doc: Document) {

    inline operator fun String.invoke(nodeContent: NodeContext<Element>.() -> Unit): Element =
        doc.createElement(this)
            .also { NodeContext(it, doc).nodeContent() }
            .also { node.appendChild(it) }

    operator fun String.rangeTo(textContent: String) =
        invoke { node.textContent = textContent }
}

fun gitSubmoduleStatus(): String =
    ByteArrayOutputStream()
        .also { out ->
            exec {
                standardOutput = out
                workingDir = rootDir.resolve("..")
                commandLine("git", "submodule", "status")
            }
        }
        .toString()
        .trim()
//endregion
