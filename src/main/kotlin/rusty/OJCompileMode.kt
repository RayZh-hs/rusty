package rusty

import rusty.asm.AsmConstructor
import rusty.asm.support.AsmContext
import rusty.core.CompileError
import rusty.core.RiscvTargetConfig
import rusty.ir.IRConstructor
import rusty.lexer.Lexer
import rusty.opt.IROptimizer
import rusty.parser.Parser
import rusty.preprocessor.Preprocessor
import rusty.semantic.SemanticConstructor
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.concurrent.thread

object OJCompileMode {
    private const val SPECIAL_FLAG = "--stdio-asm"
    private const val CLANG_PROPERTY = "rusty.ojClang"
    private const val CLANG_ENV = "RUSTY_OJ_CLANG"
    private const val TARGET_PROPERTY = "rusty.ojTarget"
    private const val TARGET_ENV = "RUSTY_OJ_TARGET"

    data class Output(val userAsm: String, val builtinAsm: String)

    data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    fun isRequested(args: Array<String>): Boolean = args.any { it == SPECIAL_FLAG }

    fun run(
        stdin: InputStream = System.`in`,
        stdout: OutputStream = System.out,
        stderr: OutputStream = System.err,
        builtinAsmProvider: () -> String = ::buildBuiltinAssembly,
    ) {
        val source = stdin.bufferedReader().use { it.readText() }
        val output = compile(source, builtinAsmProvider)
        stdout.bufferedWriter().use { writer -> writer.write(ensureTrailingNewline(output.userAsm)) }
        stderr.bufferedWriter().use { writer -> writer.write(ensureTrailingNewline(output.builtinAsm)) }
    }

    fun compile(
        source: String,
        builtinAsmProvider: () -> String = ::buildBuiltinAssembly,
    ): Output {
        CompileError.registerSource(source.split("\n"))

        val preprocessed = Preprocessor.run(source)
        val tokens = Lexer.run(preprocessed)
        val ast = Parser.run(tokens)
        val semantic = SemanticConstructor.run(ast, dumpToScreen = false)
        val ir = IRConstructor.run(semantic, dumpToScreen = false)
        val optimizedIr = IROptimizer.run(ir, dumpToScreen = false)
        val userAsm = AsmConstructor.run(AsmContext(optimizedIr), dumpToScreen = false)

        return Output(
            userAsm = userAsm,
            builtinAsm = builtinAsmProvider(),
        )
    }

    private fun buildBuiltinAssembly(): String {
        val preludeDir = Paths.get("src", "main", "kotlin", "rusty", "ir", "prelude")
        val preludeLl = requireExisting(preludeDir.resolve("prelude.ll"))
        val preludeC = requireExisting(preludeDir.resolve("prelude.c"))
        val target = System.getProperty(TARGET_PROPERTY)
            ?: System.getenv(TARGET_ENV)
            ?: "riscv64-linux-gnu"
        val clangBinary = System.getProperty(CLANG_PROPERTY)
            ?: System.getenv(CLANG_ENV)
            ?: "clang"

        val sharedArgs = listOf(
            clangBinary,
            "-S",
            "--target=$target",
            "-march=${RiscvTargetConfig.LINUX_ARCH}",
            "-mabi=${RiscvTargetConfig.LINUX_ABI}",
            "-fno-addrsig",
        )

        val preludeAsm = compileAsm(
            sharedArgs + listOf("-Wno-override-module", preludeLl.toString(), "-o", "-"),
            "prelude.ll"
        )
        val runtimeAsm = compileAsm(
            sharedArgs + listOf(
                "-O2",
                "-fno-builtin",
                "-fno-stack-protector",
                preludeC.toString(),
                "-o",
                "-",
            ),
            "prelude.c"
        )

        return listOf(preludeAsm, runtimeAsm)
            .joinToString(separator = "\n") { sanitizeBuiltinAssembly(it).trimEnd('\n', '\r') }
            .trimEnd('\n', '\r')
    }

    private fun requireExisting(path: Path): Path {
        require(Files.exists(path)) { "Required runtime source missing: $path" }
        return path
    }

    private fun compileAsm(args: List<String>, label: String): String {
        val result = runProcess(args)
        require(result.exitCode == 0) {
            buildString {
                append("Failed to compile runtime assembly from ")
                append(label)
                append(" (exit ")
                append(result.exitCode)
                append(")\nCommand: ")
                append(args.joinToString(" "))
                if (result.stderr.isNotBlank()) {
                    append("\nStderr:\n")
                    append(result.stderr)
                }
                if (result.stdout.isNotBlank()) {
                    append("\nStdout:\n")
                    append(result.stdout)
                }
            }
        }
        return result.stdout
    }

    private fun sanitizeBuiltinAssembly(asm: String): String {
        return asm
            .lineSequence()
            .filterNot { it == "\t.addrsig" || it.startsWith("\t.addrsig_sym") }
            .joinToString(separator = "\n")
    }

    private fun ensureTrailingNewline(text: String): String =
        if (text.endsWith("\n")) text else "$text\n"

    private fun runProcess(args: List<String>): ProcessResult {
        val process = ProcessBuilder(args).start()
        var stdout = ""
        var stderr = ""
        val stdoutThread = thread(start = true, name = "oj-compile-stdout") {
            stdout = process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderrThread = thread(start = true, name = "oj-compile-stderr") {
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        }
        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()
        return ProcessResult(exitCode, stdout, stderr)
    }
}
