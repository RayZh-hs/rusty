fn foo(a: i32, b: i32) -> i32 {
    a + b
}

fn main() {
    let a: i32 = foo(1, 2);
    let b: i32 = foo(3, 4);
    let c = a + b;
}