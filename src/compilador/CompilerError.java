package compilador;

/** Erro (ou aviso) detectado em qualquer fase: léxica, sintática, semântica ou de execução. */
public class CompilerError extends RuntimeException {
    private final String kind;
    private final int line;
    private final int col;

    public CompilerError(String kind, String message, int line, int col) {
        super(message, null, false, false);
        this.kind = kind;
        this.line = line;
        this.col = col;
    }

    public String kind() { return kind; }
    public int line() { return line; }
    public int col() { return col; }

    /** Formato: arquivo:linha:coluna: tipo: mensagem */
    public String format(String file) {
        StringBuilder sb = new StringBuilder(file);
        if (line > 0) {
            sb.append(':').append(line);
            if (col > 0) sb.append(':').append(col);
        }
        return sb.append(": ").append(kind).append(": ").append(getMessage()).toString();
    }
}
