package compilador;

public record Token(TokenType type, String lexeme, int line, int col) {
    @Override
    public String toString() {
        return String.format("%-10s %-14s linha %d, coluna %d", type, "'" + lexeme + "'", line, col);
    }
}
