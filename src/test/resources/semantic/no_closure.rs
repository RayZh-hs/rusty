// ! Skip
// ! Expected to fail
// ! Reason: Cannot capture environment variable `a` in inner function

fn main() {
    outer();
}

fn outer() {
    let a: i32 = 1;
    fn inner() {
        printInt(a);
    }
    inner();
}