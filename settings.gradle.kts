import org.gradle.api.GradleException

rootProject.name = "rusty"

val llvmLibPath = System.getenv("LLVM_LIB_PATH")?.takeIf { it.isNotBlank() }
if (llvmLibPath != null) {
    val expandedLlvmLibPath = when {
        llvmLibPath == "~" -> System.getProperty("user.home")
        llvmLibPath.startsWith("~/") -> System.getProperty("user.home") + llvmLibPath.removePrefix("~")
        else -> llvmLibPath
    }

    val llvmLibDir = file(expandedLlvmLibPath).canonicalFile
    if (!llvmLibDir.isDirectory) {
        throw GradleException("LLVM_LIB_PATH does not point to a directory: $llvmLibDir")
    }

    includeBuild(llvmLibDir) {
        dependencySubstitution {
            substitute(module("space.norb:llvm")).using(project(":"))
        }
    }
}
