fn main() {
    let a: &str = "Hello, world!";
    let b: [i32; 5] = [1; 5];
    if (true) {
        println(a);
    } else {
        printlnInt(b[0]);
    }
    exit(0);
}