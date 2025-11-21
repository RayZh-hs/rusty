fn inc(v: i32) -> i32 { v + 1 }
fn double(v: i32) -> i32 { v * 2 }

fn main() {
    let start: i32 = 3;
    let result: i32 = double(inc(start)) - start;
    printlnInt(result);
    exit(0);
}
