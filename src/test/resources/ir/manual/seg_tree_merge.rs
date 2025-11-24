const MAXSEG: usize = 32;

struct Food {
    cnt: i32,
    ty: i32,
}

struct SegT {
    l: i32,
    r: i32,
    lc: usize,
    rc: usize,
    val: Food,
}

fn new_segt(pool: &mut [SegT; MAXSEG], seg_cnt: &mut usize, l: i32, r: i32, val: Food) -> usize {
    *seg_cnt += 1;
    pool[*seg_cnt] = SegT { l: l, r: r, lc: 0, rc: 0, val: val };
    *seg_cnt
}

fn build(pool: &mut [SegT; MAXSEG], seg_cnt: &mut usize, l: i32, r: i32) -> usize {
    if (l == r) {
        return new_segt(pool, seg_cnt, l, r, Food { cnt: 0, ty: l });
    }
    new_segt(pool, seg_cnt, l, r, Food { cnt: 0, ty: 0 })
}

impl SegT {
    fn lc_val(&self, pool: &[SegT; MAXSEG]) -> Food {
        pool[self.lc].val
    }
    fn rc_val(&self, pool: &[SegT; MAXSEG]) -> Food {
        pool[self.rc].val
    }
}

fn update(pool: &mut [SegT; MAXSEG], seg_cnt: &mut usize, idx: usize, pos: i32, delta: i32) {
    if (idx == 0) { return; }
    let l: i32 = pool[idx].l;
    let r: i32 = pool[idx].r;
    if (l == r) {
        pool[idx].val.cnt += delta;
        return;
    }
    let mid: i32 = (l + r) / 2;
    if (pos <= mid) {
        if (pool[idx].lc == 0) {
            pool[idx].lc = build(pool, seg_cnt, l, mid);
        }
        update(pool, seg_cnt, pool[idx].lc, pos, delta);
    } else {
        if (pool[idx].rc == 0) {
            pool[idx].rc = build(pool, seg_cnt, mid + 1, r);
        }
        update(pool, seg_cnt, pool[idx].rc, pos, delta);
    }
    let mut best: Food = Food { cnt: 0, ty: 0 };
    if (pool[idx].lc != 0) {
        best = pool[idx].lc_val(pool).better(best);
    }
    if (pool[idx].rc != 0) {
        best = pool[idx].rc_val(pool).better(best);
    }
    pool[idx].val = best;
}

impl Food {
    fn better(self, other: Food) -> Food {
        if (self.cnt == other.cnt) {
            if (self.ty < other.ty) { self } else { other }
        } else if (self.cnt > other.cnt) {
            self
        } else {
            other
        }
    }
}

fn main() {
    let mut pool: [SegT; MAXSEG] = [SegT {
        l: 0,
        r: 0,
        lc: 0,
        rc: 0,
        val: Food { cnt: 0, ty: 0 },
    }; MAXSEG];
    let mut seg_cnt: usize = 0;
    let root: usize = build(&mut pool, &mut seg_cnt, 1, 2);
    update(&mut pool, &mut seg_cnt, root, 1, 3);
    update(&mut pool, &mut seg_cnt, root, 2, 1);
    printlnInt(pool[root].val.ty);
    exit(0);
}
