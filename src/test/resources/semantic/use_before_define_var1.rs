fn main() {
    outer();
    exit(0);
}

fn outer() {
    inner();
    fn inner() {
        print("Hello");
    }
}