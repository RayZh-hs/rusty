struct Foo {
    x: i32,
    y: i32,
}

impl Foo {
    fn foo(&self, offset: i32) -> i32 {
        self.x + self.y + offset
    }
}

fn main() {
    let foo: Foo = Foo { x: 10, y: 20 };
    let result = foo.foo(5);
    exit(0);
}