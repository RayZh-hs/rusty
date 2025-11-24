fn main() {
    let lcs_result: i32 = longestCommonSubsequence();
    printlnInt(lcs_result);
    exit(0);
}

fn longestCommonSubsequence() -> i32 {
    let string1_length: usize = 30;
    let string2_length: usize = 25;
    let mut string1: [i32; 30] = [0; 30];
    let mut string2: [i32; 25] = [0; 25];
    let mut dp_table: [i32; 806] = [0; 806]; // (30+1) * (25+1) table

    // Initialize strings with pattern
    initializeStrings(&mut string1, &mut string2, 17, 23);

    // Fill DP table
    let mut i: usize = 1;
    while (i <= string1_length) {
        let mut j: usize = 1;
        while (j <= string2_length) {
            if (string1[i - 1] == string2[j - 1]) {
                dp_table[i * (string2_length + 1) + j] =
                    dp_table[(i - 1) * (string2_length + 1) + (j - 1)] + 1;
            } else {
                let option1: i32 = dp_table[(i - 1) * (string2_length + 1) + j];
                let option2: i32 = dp_table[i * (string2_length + 1) + (j - 1)];

                if (option1 > option2) {
                    dp_table[i * (string2_length + 1) + j] = option1;
                } else {
                    dp_table[i * (string2_length + 1) + j] = option2;
                }
            }
            j += 1;
        }
        i += 1;
    }

    dp_table[string1_length * (string2_length + 1) + string2_length]
}

fn initializeStrings(str1: &mut [i32; 30], str2: &mut [i32; 25], seed1: i32, seed2: i32) {
    let mut current_seed: i32 = seed1;
    let mut i: usize = 0;

    while (i < 30) {
        update(&mut current_seed);
        str1[i] = current_seed % 26; // 26 different characters
        i += 1;
    }

    let mut current_seed: i32 = seed2;
    let mut j: usize = 0;
    while (j < 25) {
        update(&mut current_seed);
        str2[j] = current_seed % 26; // 26 different characters
        j += 1;
    }
}

fn update(seed: &mut i32) {
    *seed = (*seed * 1103 + 4721) % 1048583;
    if (*seed < 0) {
        *seed = -*seed;
    }
}
