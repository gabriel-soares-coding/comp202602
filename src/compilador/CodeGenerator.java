package compilador;

import java.util.ArrayList;
import java.util.List;

import compilador.Ast.*;

/**
 * Tradutor: AST -> código objeto da máquina hipotética (uma instrução por linha).
 *
 * Esquema de tradução:
 *   programa     INPP; ALME 1 (uma por variável); comandos; PARA
 *   x := e       <e>; ARMZ end(x)
 *   x := read(y) LEIT; ARMZ end(x)
 *   write(x)     CRVL end(x); IMPR
 *   a op b       <a>; <b>; SOMA|SUBT|MULT|DIVI
 *   -a           <a>; INVE
 *   if c then A else B $   <c>; DSVF Lelse; <A>; DSVI Lfim; Lelse: <B>; Lfim:
 *   if c then A $          <c>; DSVF Lfim; <A>; Lfim:
 *   while c do A $         Lini: <c>; DSVF Lfim; <A>; DSVI Lini; Lfim:
 *   a rel b      <a>; <b>; CPIG|CDES|CMAI|CPMI|CPMA|CPME
 *
 * Os rótulos são endereços absolutos (índice da instrução, a partir de 0), resolvidos por backpatching.
 * Todas as variáveis são alocadas no início (ALME), mesmo que declaradas dentro de um bloco.
 */
public class CodeGenerator {
    private final List<String> code = new ArrayList<>();
    private SemanticAnalyzer sem;

    public List<String> generate(Program p, SemanticAnalyzer sem) {
        this.sem = sem;
        code.clear();
        emit("INPP");
        for (int k = 0; k < sem.symbolCount(); k++) emit("ALME 1");
        stmts(p.body());
        emit("PARA");
        return code;
    }

    private int emit(String instr) {
        code.add(instr);
        return code.size() - 1;
    }

    private void patch(int index, String op, int target) {
        code.set(index, op + " " + target);
    }

    private int addr(Token id) { return sem.lookup(id).address; }

    private void stmts(List<Stmt> list) {
        for (Stmt s : list) stmt(s);
    }

    private void stmt(Stmt s) {
        if (s instanceof VarDecl) {
            // espaço já alocado no início do programa
        } else if (s instanceof Assign a) {
            expr(a.value());
            emit("ARMZ " + addr(a.target()));
        } else if (s instanceof Write w) {
            emit("CRVL " + addr(w.id()));
            emit("IMPR");
        } else if (s instanceof If i) {
            cond(i.cond());
            int jf = emit("DSVF ?");
            stmts(i.thenPart());
            if (i.elsePart() != null) {
                int ji = emit("DSVI ?");
                patch(jf, "DSVF", code.size());
                stmts(i.elsePart());
                patch(ji, "DSVI", code.size());
            } else {
                patch(jf, "DSVF", code.size());
            }
        } else if (s instanceof While w) {
            int start = code.size();
            cond(w.cond());
            int jf = emit("DSVF ?");
            stmts(w.body());
            emit("DSVI " + start);
            patch(jf, "DSVF", code.size());
        }
    }

    private void cond(Cond c) {
        expr(c.left());
        expr(c.right());
        emit(switch (c.op().type()) {
            case EQ -> "CPIG";
            case NE -> "CDES";
            case GE -> "CMAI";
            case LE -> "CPMI";
            case GT -> "CPMA";
            case LT -> "CPME";
            default -> throw new IllegalStateException("operador relacional inesperado: " + c.op());
        });
    }

    private void expr(Expr e) {
        if (e instanceof Num n) {
            emit("CRCT " + n.tok().lexeme());
        } else if (e instanceof Var v) {
            emit("CRVL " + addr(v.tok()));
        } else if (e instanceof Read) {
            emit("LEIT");
        } else if (e instanceof Neg n) {
            expr(n.operand());
            emit("INVE");
        } else if (e instanceof Bin b) {
            expr(b.left());
            expr(b.right());
            emit(switch (b.op().type()) {
                case PLUS -> "SOMA";
                case MINUS -> "SUBT";
                case STAR -> "MULT";
                case SLASH -> "DIVI";
                default -> throw new IllegalStateException("operador inesperado: " + b.op());
            });
        }
    }
}
