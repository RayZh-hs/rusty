struct A {
    a: i32,
    b: u32,
}

fn main() {
    let mut counter = 0;
    loop {
        counter += 1;
        if (counter > 5) {
            break;
        }
        let a: i32 = A { a: counter, b: 0 }.a;
        printlnInt(a);
    }
    exit(0);
}