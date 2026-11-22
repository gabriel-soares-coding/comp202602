package compilador;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analisador léxico escrito à mão (autômato por inspeção de caracteres).
 *
 * Tokens: palavras reservadas, id, numero_real, e os símbolos da gramática.
 * Extensões (não definidas na gramática): comentários {...} e // até o fim da linha;
 * palavras reservadas e identificadores não diferenciam maiúsculas de minúsculas.
 */
public class Lexer {
    private static final Map<String, TokenType> KEYWORDS = Map.of(
            "program", TokenType.PROGRAM, "real", TokenType.REAL,
            "if", TokenType.IF, "then", TokenType.THEN, "else", TokenType.ELSE,
            "while", TokenType.WHILE, "do", TokenType.DO,
            "write", TokenType.WRITE, "read", TokenType.READ);

    private final String src;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    public Lexer(String src) { this.src = src; }

    /** Devolve todos os tokens, terminando com EOF. Lança CompilerError no primeiro erro léxico. */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type() != TokenType.EOF);
        return tokens;
    }

    private boolean atEnd() { return pos >= src.length(); }

    private char peek(int offset) {
        int p = pos + offset;
        return p < src.length() ? src.charAt(p) : '\0';
    }

    private char advance() {
        char c = src.charAt(pos++);
        if (c == '\n') { line++; col = 1; } else { col++; }
        return c;
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private void skipBlanksAndComments() {
        while (!atEnd()) {
            char c = peek(0);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '﻿') {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                while (!atEnd() && peek(0) != '\n') advance();
            } else if (c == '{') {
                int l = line, cc = col;
                advance();
                while (!atEnd() && peek(0) != '}') advance();
                if (atEnd()) throw new CompilerError("erro léxico", "comentário '{' não terminado", l, cc);
                advance();
            } else {
                break;
            }
        }
    }

    private Token next() {
        skipBlanksAndComments();
        int l = line, c = col;
        if (atEnd()) return new Token(TokenType.EOF, "", l, c);

        char ch = peek(0);

        if (isLetter(ch)) {
            int start = pos;
            while (isLetter(peek(0)) || isDigit(peek(0))) advance();
            String lex = src.substring(start, pos);
            TokenType kw = KEYWORDS.get(lex.toLowerCase(Locale.ROOT));
            return new Token(kw != null ? kw : TokenType.ID, lex, l, c);
        }

        if (isDigit(ch)) {
            int start = pos;
            while (isDigit(peek(0))) advance();
            // a parte decimal só existe se houver dígito após o ponto; senão o '.' é o fim do programa
            if (peek(0) == '.' && isDigit(peek(1))) {
                advance();
                while (isDigit(peek(0))) advance();
            }
            if (isLetter(peek(0))) {
                while (isLetter(peek(0)) || isDigit(peek(0))) advance();
                throw new CompilerError("erro léxico",
                        "número ou identificador malformado '" + src.substring(start, pos) + "'", l, c);
            }
            return new Token(TokenType.NUM, src.substring(start, pos), l, c);
        }

        advance();
        switch (ch) {
            case ';': return new Token(TokenType.SEMI, ";", l, c);
            case ',': return new Token(TokenType.COMMA, ",", l, c);
            case '.': return new Token(TokenType.DOT, ".", l, c);
            case '(': return new Token(TokenType.LPAREN, "(", l, c);
            case ')': return new Token(TokenType.RPAREN, ")", l, c);
            case '$': return new Token(TokenType.DOLLAR, "$", l, c);
            case '=': return new Token(TokenType.EQ, "=", l, c);
            case '+': return new Token(TokenType.PLUS, "+", l, c);
            case '-': return new Token(TokenType.MINUS, "-", l, c);
            case '*': return new Token(TokenType.STAR, "*", l, c);
            case '/': return new Token(TokenType.SLASH, "/", l, c);
            case ':':
                if (peek(0) == '=') { advance(); return new Token(TokenType.ASSIGN, ":=", l, c); }
                return new Token(TokenType.COLON, ":", l, c);
            case '<':
                if (peek(0) == '>') { advance(); return new Token(TokenType.NE, "<>", l, c); }
                if (peek(0) == '=') { advance(); return new Token(TokenType.LE, "<=", l, c); }
                return new Token(TokenType.LT, "<", l, c);
            case '>':
                if (peek(0) == '=') { advance(); return new Token(TokenType.GE, ">=", l, c); }
                return new Token(TokenType.GT, ">", l, c);
            default:
                throw new CompilerError("erro léxico", "caractere inválido '" + ch + "'", l, c);
        }
    }
}
