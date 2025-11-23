struct Foo {
    bo: bool,
    int: i32,
}

fn main() {
    let mut arr: [Foo; 3] = [Foo { bo: true, int: 42 }; 3];
    arr[0].int = 7;
    printlnInt(arr[0].int);
    printlnInt(arr[1].int);
    exit(0);
}