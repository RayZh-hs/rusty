fn main() {
    outer();
}

fn outer() {
    inner();
    fn inner() {
        print("Hello");
    }
}