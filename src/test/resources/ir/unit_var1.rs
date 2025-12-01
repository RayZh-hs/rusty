// Test that () works as a unit literal
fn foo() -> () {
    let x: () = ();
    return x;
}

fn main() {
    foo();
    
    // Test assignment expression returning unit
    let mut x = 5;    
    let y: () = x = 8;
    
    exit(0);
}