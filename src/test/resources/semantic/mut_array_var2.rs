fn main() {
    let mut x: [i32; 1] = [1];
    let a: &mut &mut &mut &mut [i32; 1] = &mut &mut &mut &mut x;
    (a)[0] = 10;
    exit(0);
}