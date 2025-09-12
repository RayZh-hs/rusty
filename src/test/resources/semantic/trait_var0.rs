// ! Skip
// ! Expected to fail
// ! Reason: Partial implementation of trait methods

fn main() {
    let mut bag: Bag = Bag::new();
    let mut n: i32 = 1;
    while (n <= 5) {
        bag.insert(n * n);
        n += 1;
    }
    let check: bool = bag.contains(9);
    let dummy: bool = check;
    exit(0);
}

trait Storage {
    fn insert(&mut self, value: i32);
    fn contains(&self, value: i32) -> bool;
    fn size(&self) -> usize;
}

struct Bag {
    items: [i32; 16],
    count: usize,
}

impl Bag {
    fn new() -> Self {
        Bag { items: [0; 16], count: 0 }
    }
}

impl Storage for Bag {
    fn insert(&mut self, value: i32) {
        self.items[self.count] = value;
        self.count += 1;
    }

    fn contains(&self, value: i32) -> bool {
        let mut idx: usize = 0usize;
        while (idx < self.count) {
            if (self.items[idx] == value) {
                return true;
            }
            idx += 1;
        }
        false
    }
}
