fn main() {
    let mut x: [i32; 1] = [1];

    let a: &mut [i32; 1] = &mut x;
    a[0] = 10;

    exit(0);
}