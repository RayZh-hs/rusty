struct Sum {
    total: i32,
}

impl Sum {
    fn add(&mut self, v: i32) {
        self.total = self.total + v;
    }
}

fn main() {
    let mut acc = Sum { total: 0 };
    let mut i: i32 = 0;
    loop {
        if (i > 5) { break; }
        if (i == 2) {
            i = i + 1;
            continue;
        }
        let base: i32 = i * 3 + (i - 1);
        let even_mask: i32 = i - (i / 2) * 2;
        let term: i32 = if (even_mask == 0) { base + 1 } else { base - 1 };
        acc.add(term);
        i = i + 1;
    }
    printlnInt(acc.total);
    exit(0);
}
