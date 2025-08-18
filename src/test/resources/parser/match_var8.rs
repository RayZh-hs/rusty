// Multiple patterns via |
fn main() {
    let n = 2;
    let v = match n { 0 | 1 | 2 => 1, _ => 0 };
}
