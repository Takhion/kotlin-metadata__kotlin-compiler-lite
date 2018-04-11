import java.io.FileInputStream
import java.util.Properties

log("ENABLED")

val consts = file("build/consts.xml").let { file -> Properties().apply { loadFromXML(FileInputStream(file)) } }

val kotlinVersion = consts["kotlinVersion"] as String
val injectedDir = consts["injectedDir"]!!.let(::file)
val injectedModuleNames = consts["injectedModuleNames"].let { (it as String).split(":").toList() }

val jdkMin = 6
val jdkMax = 9

gradle.settingsEvaluated {
    val projectNames = injectedModuleNames.map { addExtraProject(injectedDir, it) }
    gradle.rootProject {
        extra["deployVersion"] = kotlinVersion
        log("set root extra deployVersion=$kotlinVersion")

        (jdkMin..jdkMax).reversed().fold(System.getenv("JAVA_HOME")) { javaHome, jdk ->
            val jdkEnvNew = "JDK_$jdk"
            val jdkEnvOld = "JDK_1$jdk"
            val jdkHome = null
                ?: findProperty(jdkEnvNew) as? String
                ?: findProperty(jdkEnvOld) as? String
                ?: System.getenv(jdkEnvNew)
                ?: System.getenv(jdkEnvOld)
                ?: javaHome
            extra[jdkEnvNew] = jdkHome
            log("set root extra $jdkEnvNew=$jdkHome")
            jdkHome
        }

        project(projectNames.first()).afterEvaluate {
            this@rootProject.defaultTasks = defaultTasks
            log("set default tasks on root project from project '$path': $defaultTasks")
        }
    }
}

fun log(message: String) = println("Settings override: $message")

fun Settings.addExtraProject(parentDir: File, dirName: String): String {
    val projectDir = parentDir.resolve(dirName)
    val logicalPath = ":${parentDir.name}:$dirName"
    include(logicalPath)
    project(logicalPath).projectDir = projectDir
    log("injected project '$logicalPath' from '$projectDir'")
    return logicalPath
}
