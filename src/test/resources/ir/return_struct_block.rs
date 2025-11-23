struct Acc {
    cur: i32,
}

fn step(acc: Acc, inc: i32) -> Acc {
    Acc { cur: acc.cur + inc }
}

fn build(a: i32, b: i32) -> Acc {
    let block_val: i32 = {
        let mut t: i32 = a + b;
        t = t * (a - b);
        if (t < 0) { -t } else { t + 1 }
    };
    {
        let seeded = Acc { cur: block_val };
        step(seeded, b - a)
    }
}

fn main() {
    let acc = build(6, 2);
    printlnInt(acc.cur);
    exit(0);
}
