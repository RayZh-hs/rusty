fn main() {
    let mut outer: i32 = 0;
    let mut acc: i32 = 0;
    loop {
        if (outer >= 4) { break; }
        let mut inner: i32 = 0;
        loop {
            if (inner >= 3) { break; }
            if (inner == 0) {
                inner = inner + 1;
                continue;
            }
            if (outer > inner) {
                acc = acc + (outer - inner);
            } else {
                acc = acc + (outer + inner);
            }
            inner = inner + 1;
        }
        outer = outer + 1;
    }
    printlnInt(acc);
    exit(0);
}
