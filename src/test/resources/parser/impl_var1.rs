impl Point {
    fn new(x: i32, y: i32) -> Self { Point { x, y } }
}

impl Foo for Point {
    fn foo(&self) {}
}

fn main() {}
