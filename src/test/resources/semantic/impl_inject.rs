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

        fn random() {}
    }
}

struct foo {}
