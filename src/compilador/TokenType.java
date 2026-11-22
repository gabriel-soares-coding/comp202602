package compilador;

/** Categorias de token da linguagem LALG-Pascal. */
public enum TokenType {
    // palavras reservadas
    PROGRAM("program"), REAL("real"), IF("if"), THEN("then"), ELSE("else"),
    WHILE("while"), DO("do"), WRITE("write"), READ("read"),
    // classes
    ID("identificador"), NUM("numero_real"),
    // símbolos
    ASSIGN(":="), COLON(":"), SEMI(";"), COMMA(","), DOT("."),
    LPAREN("("), RPAREN(")"), DOLLAR("$"),
    EQ("="), NE("<>"), GE(">="), LE("<="), GT(">"), LT("<"),
    PLUS("+"), MINUS("-"), STAR("*"), SLASH("/"),
    EOF("fim de arquivo");

    private final String desc;

    TokenType(String desc) { this.desc = desc; }

    /** Nome para mensagens de erro: símbolos e palavras entre aspas, classes sem aspas. */
    public String display() {
        return (this == ID || this == NUM || this == EOF) ? desc : "'" + desc + "'";
    }
}
