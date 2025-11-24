struct Bucket {
    data: [i32; 4],
    len: i32,
}

impl Bucket {
    fn push(&mut self, value: i32) {
        self.data[self.len as usize] = value;
        self.len += 1;
    }
}

fn main() {
    let mut buckets: [Bucket; 1] = [Bucket { data: [0; 4], len: 0 }; 1];
    buckets[0].push(10);
    buckets[0].push(20);
    printlnInt(buckets[0].data[0]);
    printlnInt(buckets[0].data[1]);
    printlnInt(buckets[0].len);
    exit(0);
}
