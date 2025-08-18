// ! Expected to fail

struct NestedOuter {
    struct NestedInner {
        x: i32,
        y: i32,
    }
    z: i32,
}

fn main() {}
