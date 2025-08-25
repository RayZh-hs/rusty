struct Foo {
    a: i32,
    b: i32,
}

fn bar(x: &Foo) {
    printInt(x.sum());
}

fn main() {
    let foo = Foo { a: 1, b: 2 };
    bar(foo);

    impl Foo {
        fn sum(&self) -> i32 {
            self.a + self.b
        }

        fn set_a(&mut self, new_a: i32) {
            self.a = new_a;
        }
    }
}