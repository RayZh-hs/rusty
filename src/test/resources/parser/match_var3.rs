// Match arm guard
fn main() {
    let n = 4;
    let r = match n { x if n == 4 => 1, _ => 0 };
}
