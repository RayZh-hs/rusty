struct Register {
    value: i32,
}

impl Register {
    fn bump(&mut self, delta: i32) -> i32 {
        self.value = self.value + delta;
        self.value
    }

    fn read(&self) -> i32 {
        self.value
    }
}

fn main() {
    let mut reg = Register { value: 1 };
    let handle: &mut Register = &mut reg;
    let step1 = handle.bump(2);
    let view: &Register = &reg;
    let peek = view.read();
    let direct = reg.read();
    printlnInt(step1 + peek + direct);
    exit(0);
}
