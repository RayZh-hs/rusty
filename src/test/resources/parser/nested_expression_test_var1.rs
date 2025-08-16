fn main() {
    let a = if (true) {
        let b = 1;
        b
    } else {
        2
    };
    print("a={}", a)
}