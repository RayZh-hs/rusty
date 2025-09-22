fn main() {
    let mut x: [i32; 1] = [1];
    let mut y: [i32; 1] = [2];

    let mut a: &mut [i32; 1] = &mut x;
    a[0] = 10;
    a = &mut y;
    a[0] = 20;
}