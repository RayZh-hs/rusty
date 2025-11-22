fn main() {
    let a = getInt();
    if (a > 0) {
        if (a < 10) {
            println("0 < a < 10");
        } else {
            println("a >= 10");
        }
    } else {
        println("a <= 0");
    }
    exit(0);
}