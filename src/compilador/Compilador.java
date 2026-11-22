package compilador;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Parte 1: fonte Pascal -> análise léxica, sintática, semântica -> código objeto em arquivo texto. */
public class Compilador {

    /**
     * @param quiet suprime as mensagens de progresso (erros e avisos sempre aparecem em stderr)
     * @return true se compilou sem erros
     */
    public static boolean compilar(Path fonte, Path saida, boolean verbose, boolean quiet) throws IOException {
        PrintStream out = System.out;
        PrintStream err = System.err;
        String file = fonte.toString();
        String src = new String(Files.readAllBytes(fonte), StandardCharsets.UTF_8);

        // 1. léxico
        List<Token> tokens;
        try {
            tokens = new Lexer(src).tokenize();
        } catch (CompilerError e) {
            err.println(e.format(file));
            return false;
        }
        if (!quiet) out.println("[1/4] Análise léxica     OK (" + (tokens.size() - 1) + " tokens)");
        if (verbose) {
            for (Token t : tokens) out.println("      " + t);
        }

        // 2. sintático
        Ast.Program prog;
        try {
            prog = new Parser(tokens).parse();
        } catch (CompilerError e) {
            err.println(e.format(file));
            return false;
        }
        if (!quiet) out.println("[2/4] Análise sintática  OK (programa '" + prog.name().lexeme() + "')");

        // 3. semântico
        SemanticAnalyzer sem = new SemanticAnalyzer();
        sem.analyze(prog);
        for (CompilerError w : sem.warnings()) err.println(w.format(file));
        if (!sem.errors().isEmpty()) {
            for (CompilerError e : sem.errors()) err.println(e.format(file));
            err.println(sem.errors().size() + " erro(s) semântico(s); código objeto não gerado.");
            return false;
        }
        if (!quiet) out.println("[3/4] Análise semântica  OK (" + sem.symbolCount() + " variáveis)");
        if (verbose) {
            out.println("      Tabela de símbolos (nome, endereço, linha da declaração):");
            for (SemanticAnalyzer.Symbol s : sem.symbols()) {
                out.println("      " + s.name + "\t" + s.address + "\t" + s.decl.line());
            }
        }

        // 4. geração de código
        List<String> code = new CodeGenerator().generate(prog, sem);
        Files.write(saida, (String.join("\n", code) + "\n").getBytes(StandardCharsets.UTF_8));
        if (!quiet) out.println("[4/4] Geração de código  OK (" + code.size() + " instruções -> " + saida + ")");
        return true;
    }
}
