struct Pair { a: i32, b: i32 }

fn swap(pair: Pair) -> Pair {
    Pair { a: pair.b, b: pair.a }
}

fn main() {
    let original = Pair { a: 7, b: -1 };
    let flipped = swap(original);
    printlnInt(flipped.a + flipped.b);
    exit(0);
}
