// Path pattern (enum variant)
enum Color { Red, Green }
fn main() {
    let c = Color::Red;
    let v = match c { Color::Red => 1, Color::Green => 2 };
}
