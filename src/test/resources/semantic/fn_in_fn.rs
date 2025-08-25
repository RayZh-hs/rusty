fn outer() {
    fn inner() {
        print("Hello");
    }
    inner();
}

fn main() {
    outer();
}