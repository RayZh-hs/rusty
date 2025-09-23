fn main() {
    if (bar()) {
        printInt(42);
    } else {
        return;
    }
}

fn bar() -> bool {
    let foo: i32 = if (true) {
        return false;
    } else {
        42
    };
    true
}