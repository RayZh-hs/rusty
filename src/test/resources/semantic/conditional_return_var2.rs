// ! Expected to fail

fn main() {
    if (bar()) {
        printInt(42);
    } else {
        return;
    }
}

fn bar() -> bool {
    let foo: i32 = if (true) {
        return 10;
    } else {
        42
    };
}