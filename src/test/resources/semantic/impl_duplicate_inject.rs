// ! Expected to fail

fn main() {
    let a: foo = foo::make();
    foo::bar();
    a.baz();

    fn impl_holder() {
        impl foo {
            fn make()->foo {
                return foo {};
            }
            fn bar() {}
            fn baz(self: Self) {}
        }
    }

    impl foo {
        fn bar() {}
    }
}

struct foo {}
