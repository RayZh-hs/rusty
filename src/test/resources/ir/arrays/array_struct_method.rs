struct Counter {
    value: i32,
}

impl Counter {
    fn inc(&mut self, delta: i32) {
        self.value += delta;
    }
}

fn main() {
    let mut counters: [Counter; 2] = [Counter { value: 0 }; 2];
    counters[0].inc(5);
    counters[1].inc(7);
    printlnInt(counters[0].value);
    printlnInt(counters[1].value);
    exit(0);
}
