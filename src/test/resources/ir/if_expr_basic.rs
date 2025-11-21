fn main() {
    let lhs: i32 = 9;
    let rhs: i32 = 4;
    let bigger: i32 = if (lhs > rhs) { lhs } else { rhs };
    let shifted: i32 = bigger + 1;
    printlnInt(shifted);
    exit(0);
}
