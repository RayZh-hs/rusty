package rusty

import rusty.asm.AsmConstructor
import rusty.asm.support.AsmContext
import rusty.core.CompileError
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

object OJCompileMode {
    private const val SPECIAL_FLAG = "--stdio-asm"

    data class Output(val userAsm: String, val builtinAsm: String)

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
        val preludeAsm = Files.readString(
            requireExisting(preludeDir.resolve("prelude.ll.riscv64-linux-gnu.s"))
        )
        val runtimeAsm = Files.readString(
            requireExisting(preludeDir.resolve("prelude.c.riscv64-linux-gnu.s"))
        )

        return listOf(preludeAsm, runtimeAsm)
            .joinToString(separator = "\n") { sanitizeBuiltinAssembly(it).trimEnd('\n', '\r') }
            .trimEnd('\n', '\r')
    }

    private fun requireExisting(path: Path): Path {
        require(Files.exists(path)) { "Required runtime assembly missing: $path" }
        return path
    }

    private fun sanitizeBuiltinAssembly(asm: String): String {
        return asm
            .lineSequence()
            .filterNot { it == "\t.addrsig" || it.startsWith("\t.addrsig_sym") }
            .joinToString(separator = "\n")
    }

    private fun ensureTrailingNewline(text: String): String =
        if (text.endsWith("\n")) text else "$text\n"
}
