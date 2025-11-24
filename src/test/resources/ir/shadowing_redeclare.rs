// Minimal repro for variable shadowing in IR generation.
// The second `let x` should shadow the first, so the final sum is 6.
fn main() {
    let mut x: i32 = 1;
    let mut total: i32 = 0;

    total += x; // uses first x (1)

    let mut x: i32 = 5; // shadows previous x
    total += x; // should use the new x (5)

    printlnInt(total);
}
