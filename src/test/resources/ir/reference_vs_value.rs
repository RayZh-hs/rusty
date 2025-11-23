struct Cell {
    v: i32,
}

fn clone_cell(cell: &Cell) -> Cell {
    Cell { v: cell.v }
}

fn add_by_value(cell: Cell, delta: i32) -> i32 {
    cell.v + delta
}

fn add_in_place(cell: &mut Cell, delta: i32) -> i32 {
    cell.v = cell.v + delta;
    cell.v
}

fn main() {
    let mut original = Cell { v: 5 };
    let untouched = add_by_value(clone_cell(&original), 2);
    let still_five = original.v;

    let mut shared = Cell { v: 3 };
    let before = shared.v;
    let after = add_in_place(&mut shared, untouched - before);

    printlnInt(still_five + before + after);
    exit(0);
}
