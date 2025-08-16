fn main() {
    let mut a = 1;
    loop {
        a += 1;
        if a > 5 {
            break;
        }
        print("a={}", a);
    }
    print(a);
}
