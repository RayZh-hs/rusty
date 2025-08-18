struct WithArray {
    arr: [u32; 16],
}

fn main() {
    let a = WithArray { arr: [0; 16] };
}
