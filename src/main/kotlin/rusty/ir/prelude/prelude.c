#include <stdio.h>
#include <stdint.h>
#include <string.h>
#define FGETS_BUFFER_SIZE 1024

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

void __c_get_str(char* buffer) {
    // get line from stdin
    fgets(buffer, FGETS_BUFFER_SIZE, stdin);
}

int32_t __c_strlen(const char* str) {
    int32_t length = 0;
    while (str[length] != '\0') {
        length++;
    }
    return length;
}

void __c_strcpy(char* dest, const char* src) {
    int32_t i = 0;
    while (src[i] != '\0') {
        dest[i] = src[i];
        i++;
    }
    dest[i] = '\0';
}

void __c_memcpy(char* dest, const char* src, int32_t n) {
    memcpy(dest, src, n);
}

void __c_itoa(int32_t value, char* str) {
    sprintf(str, "%d", value);
}