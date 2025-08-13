package rusty.lexer

enum class TokenType {
    IDENTIFIER,
    OPERATOR,
    KEYWORD,
    RESERVED_KEYWORD,
    LITERAL,
    ERROR,
}

enum class Token {
    //: Identifiers
    I_IDENTIFIER,

    //: Operators
    O_PLUS,     // +
    O_MINUS,    // -
    O_STAR,     // *
    O_DIV,      // /
    O_PERCENT,  // %
    O_EQ,   // =
    O_DOT,      // .
    O_COMMA,    // ,
    O_SEMICOLON,// ;
    O_COLUMN,   // :
    O_DOUBLE_COLON, // ::
    O_ARROW,    // ->
    O_DOUBLE_ARROW, // =>
    O_LANG,     // <
    O_RANG,     // >
    O_LPAREN,   // (
    O_RPAREN,   // )
    O_LSQUARE,  // [
    O_RSQUARE,  // ]
    O_LCURL,    // {
    O_RCURL,    // }
    O_QUESTION, // ?
    O_LEQ,      // <=
    O_GEQ,      // >=
    O_DOUBLE_EQ,// ==
    O_NEQ,      // !=
    O_DOUBLE_AND,// &&
    O_DOUBLE_OR, // ||
    O_NOT,      // !
    O_AND,      // &
    O_OR,       // |
    O_BIT_XOR,  // ^
    O_PLUS_EQ,  // +=
    O_MINUS_EQ, // -=
    O_STAR_EQ,  // *=
    O_DIV_EQ,   // /=
    O_PERCENT_EQ, // %=
    O_AND_EQ,   // &=
    O_OR_EQ,    // |=
    O_XOR_EQ,   // ^=
    O_SLFT,     // <<
    O_SRIT,     // >>
    O_SLFT_EQ,  // <<=
    O_SRIT_EQ,  // >>=
    O_AT,       // @
    O_UNDERSCORE, // _

    //: Keywords
    K_AS,
    K_BREAK,
    K_CONST,
    K_CONTINUE,
    K_CRATE,
    K_ELSE,
    K_ENUM,
    K_EXTERN,
    K_FALSE,
    K_FN,
    K_FOR,
    K_IF,
    K_IMPL,
    K_IN,
    K_LET,
    K_LOOP,
    K_MATCH,
    K_MOD,
    K_MOVE,
    K_MUT,
    K_PUB,
    K_REF,
    K_RETURN,
    K_SELF,
    K_TYPE_SELF,
    K_STATIC,
    K_STRUCT,
    K_SUPER,
    K_TRAIT,
    K_TRUE,
    K_TYPE,
    K_UNSAFE,
    K_USE,
    K_WHERE,
    K_WHILE,
    K_ASYNC,
    K_AWAIT,
    K_DYN,

    //: Reserved keywords
    K_R_ABSTRACT,
    K_R_BECOME,
    K_R_BOX,
    K_R_DO,
    K_R_FINAL,
    K_R_MACRO,
    K_R_OVERRIDE,
    K_R_PRIV,
    K_R_TYPEOF,
    K_R_UNSIZED,
    K_R_VIRTUAL,
    K_R_YIELD,
    K_R_TRY,
    K_R_GEN,

    //: (Numeric and String) Literals
    L_CHAR,
    L_STRING,
    L_BYTE_STRING,
    L_C_STRING,
    L_RAW_STRING,       // not implemented
    L_RAW_BYTE_STRING,  // not implemented
    L_RAW_C_STRING,     // not implemented
    L_BYTE,
    L_INTEGER,

    //: Error
    E_ERROR,
}

fun Token.getType(): TokenType = when (this) {
    Token.I_IDENTIFIER -> TokenType.IDENTIFIER

    // Operators
    Token.O_PLUS, Token.O_MINUS, Token.O_STAR, Token.O_DIV, Token.O_PERCENT,
    Token.O_EQ, Token.O_DOT, Token.O_COMMA, Token.O_SEMICOLON, Token.O_COLUMN,
    Token.O_DOUBLE_COLON, Token.O_ARROW, Token.O_DOUBLE_ARROW, Token.O_LANG,
    Token.O_RANG, Token.O_LPAREN, Token.O_RPAREN, Token.O_LSQUARE, Token.O_RSQUARE,
    Token.O_LCURL, Token.O_RCURL, Token.O_QUESTION, Token.O_LEQ, Token.O_GEQ,
    Token.O_DOUBLE_EQ, Token.O_NEQ, Token.O_DOUBLE_AND, Token.O_DOUBLE_OR, Token.O_NOT, Token.O_AND,
    Token.O_OR, Token.O_BIT_XOR, Token.O_PLUS_EQ, Token.O_MINUS_EQ, Token.O_STAR_EQ,
    Token.O_DIV_EQ, Token.O_PERCENT_EQ, Token.O_AND_EQ, Token.O_OR_EQ,
    Token.O_XOR_EQ, Token.O_SLFT, Token.O_SRIT, Token.O_SLFT_EQ, Token.O_SRIT_EQ, Token.O_AT,
    Token.O_UNDERSCORE
        -> TokenType.OPERATOR

    // Keywords
    Token.K_AS, Token.K_BREAK, Token.K_CONST, Token.K_CONTINUE, Token.K_CRATE,
    Token.K_ELSE, Token.K_ENUM, Token.K_EXTERN, Token.K_FALSE, Token.K_FN,
    Token.K_FOR, Token.K_IF, Token.K_IMPL, Token.K_IN, Token.K_LET, Token.K_LOOP,
    Token.K_MATCH, Token.K_MOD, Token.K_MOVE, Token.K_MUT, Token.K_PUB, Token.K_REF,
    Token.K_RETURN, Token.K_SELF, Token.K_TYPE_SELF, Token.K_STATIC, Token.K_STRUCT,
    Token.K_SUPER, Token.K_TRAIT, Token.K_TRUE, Token.K_TYPE, Token.K_UNSAFE,
    Token.K_USE, Token.K_WHERE, Token.K_WHILE, Token.K_ASYNC, Token.K_AWAIT, Token.K_DYN
        -> TokenType.KEYWORD

    // Reserved
    Token.K_R_ABSTRACT, Token.K_R_BECOME, Token.K_R_BOX, Token.K_R_DO, Token.K_R_FINAL,
    Token.K_R_MACRO, Token.K_R_OVERRIDE, Token.K_R_PRIV, Token.K_R_TYPEOF,
    Token.K_R_UNSIZED, Token.K_R_VIRTUAL, Token.K_R_YIELD, Token.K_R_TRY, Token.K_R_GEN
        -> TokenType.RESERVED_KEYWORD

    // Literals
    Token.L_CHAR, Token.L_STRING, Token.L_BYTE, Token.L_INTEGER, Token.L_BYTE_STRING,
    Token.L_C_STRING, Token.L_RAW_STRING, Token.L_RAW_BYTE_STRING, Token.L_RAW_C_STRING
        -> TokenType.LITERAL

    // Errors and default
    Token.E_ERROR
        -> TokenType.ERROR
}

fun tokenFromLiteral(literal: String): Token = when (literal) {
    "+" -> Token.O_PLUS
    "-" -> Token.O_MINUS
    "*" -> Token.O_STAR
    "/" -> Token.O_DIV
    "%" -> Token.O_PERCENT
    "=" -> Token.O_EQ
    "." -> Token.O_DOT
    "," -> Token.O_COMMA
    ";" -> Token.O_SEMICOLON
    ":" -> Token.O_COLUMN
    "::" -> Token.O_DOUBLE_COLON
    "->" -> Token.O_ARROW
    "=>" -> Token.O_DOUBLE_ARROW
    "<" -> Token.O_LANG
    ">" -> Token.O_RANG
    "(" -> Token.O_LPAREN
    ")" -> Token.O_RPAREN
    "[" -> Token.O_LSQUARE
    "]" -> Token.O_RSQUARE
    "{" -> Token.O_LCURL
    "}" -> Token.O_RCURL
    "?" -> Token.O_QUESTION
    "<=" -> Token.O_LEQ
    ">=" -> Token.O_GEQ
    "==" -> Token.O_DOUBLE_EQ
    "!=" -> Token.O_NEQ
    "&&" -> Token.O_DOUBLE_AND
    "||" -> Token.O_DOUBLE_OR
    "!" -> Token.O_NOT
    "&" -> Token.O_AND
    "|" -> Token.O_OR
    "^" -> Token.O_BIT_XOR
    "+=" -> Token.O_PLUS_EQ
    "-=" -> Token.O_MINUS_EQ
    "*=" -> Token.O_STAR_EQ
    "/=" -> Token.O_DIV_EQ
    "%=" -> Token.O_PERCENT_EQ
    "&=" -> Token.O_AND_EQ
    "|=" -> Token.O_OR_EQ
    "^=" -> Token.O_XOR_EQ
    "<<" -> Token.O_SLFT
    ">>" -> Token.O_SRIT
    "<<=" -> Token.O_SLFT_EQ
    ">>=" -> Token.O_SRIT_EQ
    "@" -> Token.O_AT
    "_" -> Token.O_UNDERSCORE

    "as" -> Token.K_AS
    "break" -> Token.K_BREAK
    "const" -> Token.K_CONST
    "continue" -> Token.K_CONTINUE
    "crate" -> Token.K_CRATE
    "else" -> Token.K_ELSE
    "enum" -> Token.K_ENUM
    "extern" -> Token.K_EXTERN
    "false" -> Token.K_FALSE
    "fn" -> Token.K_FN
    "for" -> Token.K_FOR
    "if" -> Token.K_IF
    "impl" -> Token.K_IMPL
    "in" -> Token.K_IN
    "let" -> Token.K_LET
    "loop" -> Token.K_LOOP
    "match" -> Token.K_MATCH
    "mod" -> Token.K_MOD
    "move" -> Token.K_MOVE
    "mut" -> Token.K_MUT
    "pub" -> Token.K_PUB
    "ref" -> Token.K_REF
    "return" -> Token.K_RETURN
    "self" -> Token.K_SELF
    "Self" -> Token.K_TYPE_SELF
    "type" -> Token.K_TYPE
    "static" -> Token.K_STATIC
    "struct" -> Token.K_STRUCT
    "super" -> Token.K_SUPER
    "trait" -> Token.K_TRAIT
    "true" -> Token.K_TRUE
    "unsafe" -> Token.K_UNSAFE
    "use" -> Token.K_USE
    "where" -> Token.K_WHERE
    "while" -> Token.K_WHILE
    "async" -> Token.K_ASYNC
    "await" -> Token.K_AWAIT
    "dyn" -> Token.K_DYN
    "abstract" -> Token.K_R_ABSTRACT
    "become" -> Token.K_R_BECOME
    "box" -> Token.K_R_BOX
    "do" -> Token.K_R_DO
    "final" -> Token.K_R_FINAL
    "macro" -> Token.K_R_MACRO
    "override" -> Token.K_R_OVERRIDE
    "priv" -> Token.K_R_PRIV
    "typeof" -> Token.K_R_TYPEOF
    "unsized" -> Token.K_R_UNSIZED
    "virtual" -> Token.K_R_VIRTUAL
    "yield" -> Token.K_R_YIELD
    "try" -> Token.K_R_TRY
    "gen" -> Token.K_R_GEN

    else -> Token.E_ERROR
}