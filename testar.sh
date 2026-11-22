#!/usr/bin/env bash
# Compila o projeto e roda os testes automáticos. Uso: ./testar.sh
set -u
cd "$(dirname "$0")"
mkdir -p out build_tmp
javac -d out src/compilador/*.java || exit 1
J="java -cp out compilador.Main"
fail=0

check() { # descrição, comando que deve retornar 0
  if eval "$2" >/dev/null 2>&1; then echo "OK    $1"; else echo "FALHA $1"; fail=1; fi
}

# Parte 1: código objeto idêntico ao exemplo do professor
$J compilar exemplos/correto.pas build_tmp/correto.obj >/dev/null 2>&1
check "correto.pas gera o código objeto esperado" "diff build_tmp/correto.obj exemplos/esperado/correto.obj"

# Parte 2: execução (|a-b| para 10 pares, depois o último c)
saida=$(printf '1 5\n7 3\n2 2\n10 4\n0 9\n3 8\n6 1\n5 5\n4 12\n9 2\n' | $J executar build_tmp/correto.obj 2>/dev/null | tr '\n' ' ')
check "correto.obj executa corretamente" "[ '$saida' = '4 4 0 6 9 5 5 0 8 7 7 ' ]"

saida=$($J rodar exemplos/expressoes.pas build_tmp/expressoes.obj 2>/dev/null | tr '\n' ' ')
check "expressoes.pas imprime 1.5 e 2.5" "[ '$saida' = '1.5 2.5 ' ]"

# erros devem retornar código 1 e não gerar objeto
for e in erro_lexico erro_sintatico erro_semantico; do
  rm -f build_tmp/$e.obj
  check "$e.pas é rejeitado" "! $J compilar exemplos/$e.pas build_tmp/$e.obj && [ ! -f build_tmp/$e.obj ]"
done

rm -rf build_tmp
[ $fail -eq 0 ] && echo "Todos os testes passaram." || echo "Há testes com falha."
exit $fail
