// AST types demo
struct Point { x: i32, y: i32 }

fn add(a: i32, b: i32) -> i32 { a + b }
fn mul(a: i32, b: i32) -> i32 { a * b }
fn is_positive(n: i32) -> bool { n > 0 }
fn greet() -> &str { "hi" }

fn main() {
    let i: i32 = 10;
    let j: i32 = add(i, 5);
    let m: i32 = mul(j, 2);
    let f = 2;
    let b: bool = is_positive(j);
    let t_first: i32 = i;
    let s: &str = greet();
    let p: Point = Point { x: i, y: j };
    let v: i32 = if (b) { m + 1 } else { m - 1 };
    let flag: bool = i < j && b;
    // Force some nested expressions
    let nested: i32 = add(mul(add(i, j), 3), 7);
    exit(0);
}