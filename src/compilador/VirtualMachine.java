package compilador;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Máquina hipotética (pilha de valores reais).
 * Lê o arquivo de código objeto (uma instrução por linha) e executa.
 *
 * M = pilha de dados, s = topo (começa em -1), pc = contador de instruções (começa em 0).
 * As variáveis ficam nas primeiras posições da pilha (alocadas por ALME) e são acessadas por endereço absoluto.
 *
 * Instruções:
 *   INPP            s := -1
 *   ALME n          reserva n posições (zeradas)       DALM n   libera n posições
 *   CRCT k          empilha constante k
 *   CRVL n          empilha M[n]                       ARMZ n   M[n] := topo; desempilha
 *   SOMA SUBT MULT DIVI    M[s-1] := M[s-1] op M[s]; desempilha
 *   INVE            M[s] := -M[s]
 *   CPME CPMA CPIG CDES CPMI CMAI   comparação (<, >, =, <>, <=, >=) -> 1 ou 0
 *   CONJ DISJ NEGA  e, ou, não (valores 1/0)
 *   DSVI n          pc := n
 *   DSVF n          se topo = 0, pc := n; senão pc++ ; desempilha
 *   LEIT            lê um número e empilha            IMPR     imprime topo; desempilha
 *   PARA            encerra a execução
 */
public class VirtualMachine {

    private record Instr(String op, int n, double v, int srcLine) {}

    private static final Set<String> NO_ARG = Set.of(
            "INPP", "PARA", "SOMA", "SUBT", "MULT", "DIVI", "INVE",
            "CPME", "CPMA", "CPIG", "CDES", "CPMI", "CMAI", "CONJ", "DISJ", "NEGA", "LEIT", "IMPR");
    private static final Set<String> INT_ARG = Set.of("ALME", "DALM", "CRVL", "ARMZ", "DSVI", "DSVF");

    private final List<Instr> prog = new ArrayList<>();
    private double[] m = new double[256];
    private int s = -1;
    private int pc = 0;
    private StringTokenizer pendingInput;

    /** Carrega e valida o arquivo de código objeto. Lança CompilerError (kind "erro de formato"). */
    public VirtualMachine(Path objFile) throws IOException {
        String text = new String(Files.readAllBytes(objFile), StandardCharsets.UTF_8);
        String[] lines = text.split("\\R", -1);
        for (int k = 0; k < lines.length; k++) {
            String line = lines[k].trim();
            if (line.isEmpty()) continue;
            int lineNo = k + 1;
            String[] parts = line.split("\\s+");
            String op = parts[0].toUpperCase(Locale.ROOT);

            if (NO_ARG.contains(op)) {
                if (parts.length != 1) throw format("a instrução " + op + " não recebe argumento", lineNo);
                prog.add(new Instr(op, 0, 0, lineNo));
            } else if (INT_ARG.contains(op)) {
                if (parts.length != 2) throw format("a instrução " + op + " exige 1 argumento inteiro", lineNo);
                int n;
                try {
                    n = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    throw format("argumento inválido '" + parts[1] + "' para " + op, lineNo);
                }
                if (n < 0) throw format("argumento negativo para " + op, lineNo);
                prog.add(new Instr(op, n, 0, lineNo));
            } else if (op.equals("CRCT")) {
                if (parts.length != 2) throw format("a instrução CRCT exige 1 argumento numérico", lineNo);
                double v;
                try {
                    v = Double.parseDouble(parts[1]);
                } catch (NumberFormatException e) {
                    throw format("constante inválida '" + parts[1] + "'", lineNo);
                }
                prog.add(new Instr(op, 0, v, lineNo));
            } else {
                throw format("instrução desconhecida '" + parts[0] + "'", lineNo);
            }
        }
        if (prog.isEmpty()) throw format("arquivo de código objeto vazio", 0);
        for (Instr in : prog) {
            if ((in.op.equals("DSVI") || in.op.equals("DSVF")) && in.n >= prog.size()) {
                throw format("desvio para endereço inexistente " + in.n, in.srcLine);
            }
        }
    }

    private static CompilerError format(String msg, int line) {
        return new CompilerError("erro de formato", msg, line, 0);
    }

    // ---------- execução ----------

    /** Executa o programa. Lança CompilerError (kind "erro de execução") em caso de falha. */
    public void run(BufferedReader in, PrintStream out, boolean prompt) throws IOException {
        s = -1;
        pc = 0;
        while (true) {
            if (pc < 0 || pc >= prog.size()) {
                throw new CompilerError("erro de execução", "fim do código sem a instrução PARA", 0, 0);
            }
            Instr i = prog.get(pc);
            switch (i.op) {
                case "INPP" -> { s = -1; pc++; }
                case "PARA" -> { out.flush(); return; }
                case "ALME" -> {
                    reserve(i.n);
                    Arrays.fill(m, s + 1, s + 1 + i.n, 0.0);
                    s += i.n;
                    pc++;
                }
                case "DALM" -> {
                    if (i.n > s + 1) throw rt(i, "DALM libera mais posições do que existem");
                    s -= i.n;
                    pc++;
                }
                case "CRCT" -> { push(i.v); pc++; }
                case "CRVL" -> {
                    checkAddr(i);
                    push(m[i.n]);
                    pc++;
                }
                case "ARMZ" -> {
                    double v = pop(i);
                    checkAddr(i);
                    m[i.n] = v;
                    pc++;
                }
                case "SOMA" -> { double b = pop(i), a = pop(i); push(a + b); pc++; }
                case "SUBT" -> { double b = pop(i), a = pop(i); push(a - b); pc++; }
                case "MULT" -> { double b = pop(i), a = pop(i); push(a * b); pc++; }
                case "DIVI" -> {
                    double b = pop(i), a = pop(i);
                    if (b == 0.0) throw rt(i, "divisão por zero");
                    push(a / b);
                    pc++;
                }
                case "INVE" -> { push(-pop(i)); pc++; }
                case "CPME" -> { double b = pop(i), a = pop(i); push(a < b ? 1 : 0); pc++; }
                case "CPMA" -> { double b = pop(i), a = pop(i); push(a > b ? 1 : 0); pc++; }
                case "CPIG" -> { double b = pop(i), a = pop(i); push(a == b ? 1 : 0); pc++; }
                case "CDES" -> { double b = pop(i), a = pop(i); push(a != b ? 1 : 0); pc++; }
                case "CPMI" -> { double b = pop(i), a = pop(i); push(a <= b ? 1 : 0); pc++; }
                case "CMAI" -> { double b = pop(i), a = pop(i); push(a >= b ? 1 : 0); pc++; }
                case "CONJ" -> { double b = pop(i), a = pop(i); push(a == 1 && b == 1 ? 1 : 0); pc++; }
                case "DISJ" -> { double b = pop(i), a = pop(i); push(a == 1 || b == 1 ? 1 : 0); pc++; }
                case "NEGA" -> { push(1 - pop(i)); pc++; }
                case "DSVI" -> pc = i.n;
                case "DSVF" -> pc = (pop(i) == 0.0) ? i.n : pc + 1;
                case "LEIT" -> { push(readNumber(in, out, prompt, i)); pc++; }
                case "IMPR" -> { out.println(formatNumber(pop(i))); pc++; }
                default -> throw rt(i, "instrução não implementada");
            }
        }
    }

    private CompilerError rt(Instr i, String msg) {
        return new CompilerError("erro de execução", msg + " (instrução " + pc + ": " + i.op + ")", i.srcLine, 0);
    }

    private void reserve(int extra) {
        int needed = s + 1 + extra;
        if (needed > m.length) m = Arrays.copyOf(m, Math.max(needed, m.length * 2));
    }

    private void push(double v) {
        reserve(1);
        m[++s] = v;
    }

    private double pop(Instr i) {
        if (s < 0) throw rt(i, "pilha vazia");
        return m[s--];
    }

    private void checkAddr(Instr i) {
        if (i.n > s) throw rt(i, "endereço " + i.n + " fora da área alocada");
    }

    private double readNumber(BufferedReader in, PrintStream out, boolean prompt, Instr i) throws IOException {
        if (prompt) {
            out.print("? ");
            out.flush();
        }
        while (pendingInput == null || !pendingInput.hasMoreTokens()) {
            String line = in.readLine();
            if (line == null) throw rt(i, "fim da entrada ao executar LEIT");
            pendingInput = new StringTokenizer(line);
        }
        String tok = pendingInput.nextToken();
        try {
            return Double.parseDouble(tok.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw rt(i, "entrada inválida '" + tok + "' (esperado um número)");
        }
    }

    /** Inteiros sem casa decimal; reais com até 12 dígitos significativos. */
    static String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return Double.toString(v);
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();
    }
}
