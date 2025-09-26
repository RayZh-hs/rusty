// ! Expected to fail
// ! Reason: In expressions, `_` can only be used on the left-hand side of an assignment

fn main() {
    _;
    exit(0);
}