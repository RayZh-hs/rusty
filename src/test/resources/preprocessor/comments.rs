/* -------------------------------------------- */
/*                                              */
/*   ,---.                         |            */
/*   |    ,---.,-.-.,-.-.,---.,---.|--- ,---.   */
/*   |    |   || | || | ||---'|   ||    `---.   */
/*   `---'`---'` ' '` ' '`---'`   '`---'`---'   */
/*                                              */
/* A testcase by com.norb to test comment handling. */
/* -------------------------------------------- */

fn main() {
    let a = 0;  // this is a line comment
    let b = 1;  /* this is a block comment */
    let c /* this is */ = /* also a block comment */ 3;
    /* Block /* comments /* can be NESTED */ */ */
    let d = "this is a /* STRING */ not a comment";
    let e = b' this is a /* byte literal */';
    let f = "\"you need to consider /* ESCAPES */ too \\";
    let g
    =
"and MULTI
-LINE strings

    "; // too
    let h = "\
This back-slash denotes /* NO LINE-BREAK */
";  return;
}