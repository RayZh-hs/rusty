fn main() {
    let mut matrix: [[i32; 3]; 2] = [[1, 2, 3], [4, 5, 6]];
    printlnInt(matrix[0][0]);
    printlnInt(matrix[1][2]);
    
    matrix[1][0] = 10;
    printlnInt(matrix[1][0]);
    
    let mut sum: i32 = 0;
    let mut i: i32 = 0;
    loop {
        if (i >= 2) { break; }
        let mut j: i32 = 0;
        loop {
            if (j >= 3) { break; }
            sum = sum + matrix[i][j];
            j = j + 1;
        }
        i = i + 1;
    }
    printlnInt(sum);
    exit(0);
}
