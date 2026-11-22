package compilador;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import compilador.Ast.*;

/**
 * Analisador semântico. Percorre a AST na ordem do programa, mantendo a tabela de símbolos.
 *
 * Verificações:
 *  - variável declarada duas vezes;
 *  - uso (leitura, escrita, atribuição, read) de variável não declarada ou usada antes da declaração;
 *  - divisão por literal zero.
 * Avisos:
 *  - variável declarada e nunca usada;
 *  - read(x) atribuído a variável diferente de x.
 *
 * Só existe o tipo real, então não há verificação de compatibilidade de tipos.
 * O endereço de cada variável é a ordem de declaração (0, 1, 2, ...).
 */
public class SemanticAnalyzer {

    public static class Symbol {
        public final String name;
        public final int address;
        public final Token decl;
        boolean used = false;

        Symbol(String name, int address, Token decl) {
            this.name = name;
            this.address = address;
            this.decl = decl;
        }
    }

    private final Map<String, Symbol> table = new LinkedHashMap<>();
    private final List<CompilerError> errors = new ArrayList<>();
    private final List<CompilerError> warnings = new ArrayList<>();

    public void analyze(Program p) {
        stmts(p.body());
        for (Symbol s : table.values()) {
            if (!s.used) warn("variável '" + s.decl.lexeme() + "' declarada mas nunca usada", s.decl);
        }
    }

    public List<CompilerError> errors() { return errors; }
    public List<CompilerError> warnings() { return warnings; }
    public Collection<Symbol> symbols() { return table.values(); }
    public int symbolCount() { return table.size(); }

    public Symbol lookup(Token id) { return table.get(key(id)); }

    // ---------- percurso ----------

    private static String key(Token id) { return id.lexeme().toLowerCase(Locale.ROOT); }

    private void error(String msg, Token at) {
        errors.add(new CompilerError("erro semântico", msg, at.line(), at.col()));
    }

    private void warn(String msg, Token at) {
        warnings.add(new CompilerError("aviso semântico", msg, at.line(), at.col()));
    }

    private void stmts(List<Stmt> list) {
        for (Stmt s : list) stmt(s);
    }

    private void stmt(Stmt s) {
        if (s instanceof VarDecl d) {
            for (Token id : d.names()) {
                Symbol prev = table.get(key(id));
                if (prev != null) {
                    error("variável '" + id.lexeme() + "' já declarada na linha " + prev.decl.line(), id);
                } else {
                    table.put(key(id), new Symbol(key(id), table.size(), id));
                }
            }
        } else if (s instanceof Assign a) {
            Symbol target = table.get(key(a.target()));
            if (target == null) error("variável '" + a.target().lexeme() + "' não declarada", a.target());
            if (a.value() instanceof Read r && target != null && !key(r.id()).equals(key(a.target()))) {
                warn("read(" + r.id().lexeme() + ") é atribuído a '" + a.target().lexeme()
                        + "'; o identificador dentro de read não é usado na execução", r.id());
            }
            expr(a.value());
        } else if (s instanceof Write w) {
            use(w.id());
        } else if (s instanceof If i) {
            cond(i.cond());
            stmts(i.thenPart());
            if (i.elsePart() != null) stmts(i.elsePart());
        } else if (s instanceof While w) {
            cond(w.cond());
            stmts(w.body());
        }
    }

    private void cond(Cond c) {
        expr(c.left());
        expr(c.right());
    }

    private void expr(Expr e) {
        if (e instanceof Var v) {
            use(v.tok());
        } else if (e instanceof Read r) {
            use(r.id());
        } else if (e instanceof Neg n) {
            expr(n.operand());
        } else if (e instanceof Bin b) {
            expr(b.left());
            expr(b.right());
            if (b.op().type() == TokenType.SLASH && b.right() instanceof Num n
                    && Double.parseDouble(n.tok().lexeme()) == 0.0) {
                error("divisão por zero", b.op());
            }
        }
        // Num: nada a verificar
    }

    private void use(Token id) {
        Symbol s = table.get(key(id));
        if (s == null) {
            error("variável '" + id.lexeme() + "' não declarada", id);
        } else {
            s.used = true;
        }
    }
}
