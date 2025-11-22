#include <stdio.h>
#include <stdint.h>

void __c_print_int(int32_t value) {
    printf("%d", value);
}

void __c_println_int(int32_t value) {
    printf("%d\n", value);
}

void __c_print_str(const char* str) {
    printf("%s", str);
}

void __c_println_str(const char* str) {
    printf("%s\n", str);
}

int32_t __c_get_int() {
    int32_t value;
    scanf("%d", &value);
    return value;
}

int32_t __c_strlen(const char* str) {
    int32_t length = 0;
    while (str[length] != '\0') {
        length++;
    }
    return length;
}
