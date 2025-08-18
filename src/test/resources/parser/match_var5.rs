// Identifier binding with @ pattern
fn main() {
    let n = 5;
    let r = match n { x @ _ => x };
}
