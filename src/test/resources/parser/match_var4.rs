// Tuple pattern destructuring
fn main() {
    let t = (1, 2);
    let r = match t { (a, b) => a, _ => 0 };
}
