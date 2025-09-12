struct Foo {
    a: i32,
    b: i32,
}

fn bar(x: &Foo) {
    let a: i32 = x.a;
    let b: i32 = x.b;
    printInt(a + b);
}

fn main() {
    let foo: Foo = Foo { a: 1, b: 2 };
    bar(&foo);
}