fn factorial(n: i32) -> i32 {
    if (n <= 1) {
        1
    } else {
        n * factorial(n - 1)
    }
}

fn main() {
    let mut total: i32 = 0;
    let mut i: i32 = 1;
    loop {
        if (i > 5) { break; }
        total = total + factorial(i);
        i = i + 1;
    }
    printlnInt(total);
    exit(0);
}
