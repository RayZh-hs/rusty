fn main() {
    let mut acc: i32 = 0;
    let mut i: i32 = 0;
    loop {
        if (i >= 5) { break; }
        if (i == 2) {
            i = i + 1;
            continue;
        }
        acc = acc + (i * 2);
        i = i + 1;
    }
    printlnInt(acc);
    exit(0);
}
