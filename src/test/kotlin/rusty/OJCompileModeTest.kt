package rusty

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OJCompileModeTest {
    @Test
    fun `oj mode splits user asm and builtin asm across streams`() {
        val source = """
            fn main() {
                println("Hello, world!");
                exit(0);
            }
        """.trimIndent()
        val stdout = java.io.ByteArrayOutputStream()
        val stderr = java.io.ByteArrayOutputStream()

        OJCompileMode.run(
            stdin = source.byteInputStream(),
            stdout = stdout,
            stderr = stderr,
            builtinAsmProvider = { ".globl main\nmain:\n    ret\n" },
        )

        val userAsm = stdout.toString(Charsets.UTF_8)
        val builtinAsm = stderr.toString(Charsets.UTF_8)

        assertContains(userAsm, ".globl user.func.main")
        assertContains(userAsm, ".type user.func.main, @function")
        assertContains(userAsm, "call prelude.func.println")
        assertEquals(".globl main\nmain:\n    ret\n", builtinAsm)
    }
}
