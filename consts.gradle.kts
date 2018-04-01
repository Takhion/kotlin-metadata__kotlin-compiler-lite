import java.io.FileInputStream
import java.util.Properties
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import org.gradle.kotlin.dsl.getValue

val superRootDir: File by extra
val consts: Properties by extra(object : Properties() {

    val codeVersion by "1.0.0"
    val kotlinVersion by "1.2.30"

    val gitHubUser by "Takhion"
    val gitHubRepo by "kotlin-metadata__kotlin-compiler-lite"
    val gitHubRepoDomain by "github.com/$gitHubUser/$gitHubRepo"

    val gitTag by "v$codeVersion"
    val gitRepo by "$gitHubRepoDomain.git"

    val mainRepoUrl by "https://$gitHubRepoDomain"
    val taggedRepoUrl by "$mainRepoUrl/tree/$gitTag"

    val libName by "kotlin-compiler-lite"
    val libDescription by "A subset of the Kotlin compiler to be used by kotlin-metadata."
    val libUrl by mainRepoUrl

    val libGroupId by "me.eugeniomarletti.kotlin.metadata"
    val libArtifactId by "kotlin-compiler-lite"
    val libVersion by "$codeVersion-k-$kotlinVersion"

    val shadowSubPackage by "shadow"
    val libPackage by "$libGroupId.$shadowSubPackage"

    val publicationName by libArtifactId.split("-").joinToString("") { it.capitalize() }.decapitalize()

    val authorName by "Eugenio Marletti"

    val licenseName by "MIT"
    val licenseFile by superRootDir.resolve("LICENSE")
    val licenseUrl by "$mainRepoUrl/blob/$gitTag/${licenseFile.toRelativeString(superRootDir)}"

    val issuesSystem by "GitHub"
    val issuesUrl by "$mainRepoUrl/issues"

    val ciSystem by "CircleCI"
    val ciUrl by "https://circleci.com/gh/$gitHubUser/$gitHubRepo"

    val kotlinDir by superRootDir.resolve("kotlin")
    val outputDir by superRootDir.resolve("build/out")
    val initFile by superRootDir.resolve("kotlin.init.gradle")

    val injectedParent by "__injected"
    val injectedDir by superRootDir.resolve(injectedParent)

    val injectedModuleNames by listOf("override", "empty")
    val injectedModuleDirs by injectedModuleNames.map(injectedDir::resolve)

    val projectEmpty by ":$injectedParent:empty"
    val projectOverride by ":$injectedParent:override"

    val projectReflect by ":kotlin-reflect"
    val projectReflectApi by ":kotlin-reflect-api"
    val projectBuildCommon by ":kotlin-build-common"
    val projectDeserialization by ":core:deserialization"
    val projectDescriptorsRuntime by ":core:descriptors.runtime"

    operator fun <T> T.provideDelegate(thisRef: Properties, property: KProperty<*>) =
        object : ReadOnlyProperty<Properties, T> {
            init {
                thisRef[property.name] = this@provideDelegate
            }

            override fun getValue(thisRef: Properties, property: KProperty<*>) = this@provideDelegate
        }
})
