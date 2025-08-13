fn main() {
    let a = {
        let b = 1;
        b
    };
    print("a={} b={}", a, b);   // error: b is out of scope
}