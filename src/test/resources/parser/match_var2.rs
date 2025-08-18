// Arm value is a block expression
fn main() {
    let v = match 3 {
        0 => { let a = 1; a },
        _ => { 5 }
    };
    let u = v;
}
