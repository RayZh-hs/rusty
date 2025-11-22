struct Point {
    x: i32,
    y: i32,
}

impl Point {
    fn from(x: i32, y: i32) -> Point {
        Point { x: x, y: y }
    }

    fn swap(self) -> Point {
        Point { x: self.y, y: self.x }
    }

    fn print(&self) {
        print("(");
        printInt(self.x);
        print(", ");
        printInt(self.y);
        print(")");
    }
}

fn main() {
    let p = Point::from(3, 4);
    p.print();
    print("\n");
    let swapped_p = p.swap();
    swapped_p.print();
    print("\n");
    exit(0);
}