fn main() {
    let mut arr: [i32; 5] = [1, 2, 3, 4, 5];
    printlnInt(arr[0]);
    printlnInt(arr[4]);
    arr[2] = 10;
    printlnInt(arr[2]);
    
    let mut sum: i32 = 0;
    let mut i: i32 = 0;
    loop {
        if (i >= 5) { break; }
        sum = sum + arr[i];
        i = i + 1;
    }
    printlnInt(sum);
}
