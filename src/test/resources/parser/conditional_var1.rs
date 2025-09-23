struct b {
    x: bool,
}

fn main() {
    if (true && {
        let p = b { x: true };
        p.x
    }) {
        print("true")
    }
}