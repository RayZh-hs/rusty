fn mix(a: i32, b: i32, c: i32, d: i32) -> i32 {
    let left: i32 = a + b * c;
    let right: i32 = (a - d) * (c + 1);
    let tail: i32 = d / 2 - b;
    left + right - tail
}

fn main() {
    let result = mix(3, 4, 5, 8);
    printlnInt(result);
    exit(0);
}
