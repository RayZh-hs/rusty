struct Foo {
    a: i32,
    b: i32,
}

fn bar(x: &Foo) {
    let a = x.a;
    let b = x.b;
    printInt(a + b);
}

fn main() {
    let foo = Foo { a: 1, b: 2 };
    bar(foo);
}