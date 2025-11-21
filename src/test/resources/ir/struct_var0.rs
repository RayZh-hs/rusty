struct Foo {
    bar: Bar
}

struct Bar {
    int: i32
}

fn main() {
    let bar = Bar { int: 42 };
    let foo = Foo { bar: bar };
    exit(0)
}