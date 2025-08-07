fn main() {
    let normal = "hello\nworld\t/* not a comment */";
    let raw1 = r"hello\nworld\t/* also not a comment */";
    let raw2 = r#"hello "world" \n\t/* still not a comment */"#;
    let raw3 = r##"hello #"world"# \n\t/* nope, not a comment */"##;
    let raw_byte1 = br"hello\nworld\t/* not a comment */";
    let raw_byte2 = br#"hello "world" \n\t/* not a comment */"#;
    let raw_c1 = cr"hello\nworld\t/* not a comment */";
    let raw_c2 = cr#"hello "world" \n\t/* not a comment */"#; // is a comment
    let multiline = r"line1
line2
line3";

    println!("done");
}
