struct Foo {
    a: i32,
    b: i32,
}

fn main() {
    let foo: Foo = Foo { a: 1, b: 2 };
    let a: i32 = foo.a;
    let b: i32 = foo.b;
    exit(0);
}