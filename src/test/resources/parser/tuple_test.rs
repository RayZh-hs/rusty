// tuple_test.rs
// A Norb's testcase: Designed to validate the parser's ability at handling unit types and 1-tuples

fn main() {
    let _ = ();
    let a = 1;
    let b = (1);
    let c = (1,);
    let d = (1, 2);
    let e = (1, 2,);
    print("num:{} num:{} tuple:{} tuple:{} tuple:{}", a, b, c, d, e);
}