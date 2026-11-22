package compilador;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto de entrada.
 *   compilar <fonte.pas> [saida.obj]   Parte 1: análises + tradução para arquivo de código objeto
 *   executar <codigo.obj>              Parte 2: lê o código objeto e executa
 *   rodar    <fonte.pas> [saida.obj]   Parte 1 seguida da Parte 2
 * Opção: -v / --verbose (lista de tokens e tabela de símbolos).
 */
public class Main {

    private static final String USO = """
            Uso:
              java -cp out compilador.Main compilar <fonte.pas> [saida.obj] [-v]
              java -cp out compilador.Main executar <codigo.obj>
              java -cp out compilador.Main rodar    <fonte.pas> [saida.obj] [-v]

            compilar : análise léxica, sintática e semântica; grava o código objeto em arquivo texto
            executar : lê o arquivo de código objeto e executa na máquina hipotética
            rodar    : compilar + executar
            -v       : mostra tokens e tabela de símbolos
            """;

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        boolean verbose = false;
        List<String> pos = new ArrayList<>();
        for (String a : args) {
            if (a.equals("-v") || a.equals("--verbose")) verbose = true;
            else pos.add(a);
        }
        if (pos.isEmpty()) {
            System.err.print(USO);
            System.exit(2);
        }

        String cmd = pos.get(0);
        int code;
        try {
            switch (cmd) {
                case "compilar" -> {
                    if (pos.size() < 2 || pos.size() > 3) { System.err.print(USO); code = 2; break; }
                    Path src = Path.of(pos.get(1));
                    Path obj = pos.size() == 3 ? Path.of(pos.get(2)) : defaultObj(src);
                    code = Compilador.compilar(src, obj, verbose, false) ? 0 : 1;
                }
                case "executar" -> {
                    if (pos.size() != 2) { System.err.print(USO); code = 2; break; }
                    code = executar(Path.of(pos.get(1)));
                }
                case "rodar" -> {
                    if (pos.size() < 2 || pos.size() > 3) { System.err.print(USO); code = 2; break; }
                    Path src = Path.of(pos.get(1));
                    Path obj = pos.size() == 3 ? Path.of(pos.get(2)) : defaultObj(src);
                    code = Compilador.compilar(src, obj, verbose, true) ? executar(obj) : 1;
                }
                default -> { System.err.print(USO); code = 2; }
            }
        } catch (NoSuchFileException e) {
            System.err.println("erro: arquivo não encontrado: " + e.getFile());
            code = 2;
        } catch (IOException e) {
            System.err.println("erro de E/S: " + e.getMessage());
            code = 2;
        }
        System.exit(code);
    }

    private static Path defaultObj(Path src) {
        String name = src.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Path parent = src.getParent();
        return parent == null ? Path.of(base + ".obj") : parent.resolve(base + ".obj");
    }

    /** Parte 2: lê o arquivo de código objeto e executa. */
    private static int executar(Path obj) throws IOException {
        try {
            VirtualMachine vm = new VirtualMachine(obj);
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            vm.run(in, System.out, System.console() != null);
            return 0;
        } catch (CompilerError e) {
            System.err.println(e.format(obj.toString()));
            return 1;
        }
    }
}
