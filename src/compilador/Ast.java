package compilador;

import java.util.List;

/** Árvore sintática abstrata produzida pelo parser e consumida pelo semântico e pelo gerador. */
public final class Ast {
    private Ast() {}

    public interface Stmt {}
    public interface Expr {}

    public record Program(Token name, List<Stmt> body) {}

// comandos f
    public record VarDecl(Token type, List<Token> names) implements Stmt {}
    public record Assign(Token target, Expr value) implements Stmt {}
    public record Write(Token id) implements Stmt {}
    /** elsePart é null quando não há 'else'. */
    public record If(Cond cond, List<Stmt> thenPart, List<Stmt> elsePart) implements Stmt {}
    public record While(Cond cond, List<Stmt> body) implements Stmt {}

    public record Cond(Expr left, Token op, Expr right) {}

    // expressões
    public record Num(Token tok) implements Expr {}
    public record Var(Token tok) implements Expr {}
    public record Read(Token id) implements Expr {}
    public record Neg(Token op, Expr operand) implements Expr {}
    public record Bin(Token op, Expr left, Expr right) implements Expr {}
}
