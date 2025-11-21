fn add(a: i32, b: i32) -> i32 { a + b }

fn main() {
    let x: i32 = getInt();
    let y: i32 = getInt();
    let total: i32 = add(x, y);
    printlnInt(total);
    exit(0);
}
