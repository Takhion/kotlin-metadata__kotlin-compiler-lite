import java.io.FileInputStream
import java.util.Properties

log("ENABLED")

val consts = file("build/consts.xml").let { file -> Properties().apply { loadFromXML(FileInputStream(file)) } }

val kotlinVersion = consts["kotlinVersion"] as String
val injectedDir = consts["injectedDir"]!!.let(::file)
val injectedModuleNames = consts["injectedModuleNames"].let { (it as String).split(":").toList() }

val jdk6 = 16
val jdk8 = 18

gradle.settingsEvaluated {
    val projectNames = injectedModuleNames.map { addExtraProject(injectedDir, it) }
    gradle.rootProject {
        extra["deployVersion"] = kotlinVersion
        log("set root extra deployVersion=$kotlinVersion")

        (jdk6..jdk8).reversed().fold(System.getenv("JAVA_HOME")) { javaHome, jdk ->
            val jdkEnv = "JDK_$jdk"
            val jdkHome = findProperty(jdkEnv) as? String ?: System.getenv(jdkEnv) ?: javaHome
            extra[jdkEnv] = jdkHome
            log("set root extra $jdkEnv=$jdkHome")
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
