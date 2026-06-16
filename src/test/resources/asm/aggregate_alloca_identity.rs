struct Block {
    data: [i32; 16],
    seed: i32,
}

fn make_block(seed: i32) -> Block {
    let mut data: [i32; 16] = [seed; 16];
    let mut i: i32 = 0;
    while (i < 16) {
        data[i as usize] = seed + i * 3;
        i = i + 1;
    }
    Block { data: data, seed: seed }
}

fn checksum(block: &Block) -> i32 {
    let mut sum: i32 = block.seed;
    let mut i: i32 = 0;
    while (i < 16) {
        sum = sum + block.data[i as usize];
        i = i + 1;
    }
    sum
}

fn main() {
    let first: Block = make_block(2);
    {
        let first: Block = make_block(7);
        printlnInt(checksum(&first));
    }
    printlnInt(checksum(&first));
    exit(0);
}
