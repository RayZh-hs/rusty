fn abs(v: i32) -> i32 {
    if (v < 0) { -v } else { v }
}

fn gcd(a: i32, b: i32) -> i32 {
    let mut x: i32 = abs(a);
    let mut y: i32 = abs(b);
    loop {
        if (y == 0) { break; }
        if (x > y) {
            x = x - y;
        } else {
            y = y - x;
        }
    }
    x
}

fn main() {
    let first: i32 = getInt();
    let second: i32 = getInt();
    let third: i32 = getInt();
    let sum: i32 = gcd(first, second) + gcd(second, third);
    printlnInt(sum);
    exit(0);
}
