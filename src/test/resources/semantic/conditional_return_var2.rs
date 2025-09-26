// ! Expected to fail

fn main() {
    if (bar()) {
        printInt(42);
    } else {
        return;
    }
    exit(0);
}

fn bar() -> bool {
    let foo: i32 = if (true) {
        return 10;
    } else {
        42
    };
    true
}