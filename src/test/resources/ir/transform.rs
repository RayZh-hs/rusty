fn foo() -> [i32; 3] {
    [1, 2, 3]
}

fn main() {
    let arr = foo();
    let mut i = 0;
    loop {
        printlnInt(arr[i]);
        i += 1;
        if (i >= 3) {
            break;
        }
    }
    exit(0);
}