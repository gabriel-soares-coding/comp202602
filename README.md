# Compilador LALG-Pascal em Java

Trabalho de Compiladores (Comp 2). Linguagem: Pascal (gramática em `gramatica.txt`).
Análise sintática: **descendente recursiva**. Implementação em Java.

## Declaração de uso de IA

Este projeto foi desenvolvido com auxílio da IA **Claude (Anthropic)**, modelo configurado `claude-sonnet-5-5`, usada via aplicativo Web Claude. A IA auxiliou o discente a escrever o código-fonte, ajustar os exemplos de teste para maior cobertura de erros e este README, e fez a verificação da gramática descrita abaixo. O código foi compilado e testado com os exemplos fornecidos (o código objeto gerado é idêntico ao `codigo-objeto-exemplo.txt`).

## Requisitos

- JDK 17 ou superior (testado com JDK 21).

## Compilar o projeto

Linux/macOS/Windows (na pasta do projeto):

```
javac -d out src/compilador/*.java
```

## Executar

O programa tem dois modos independentes, que correspondem às duas partes do trabalho:

**Parte 1 – compilar** (lê o `.pas`, faz as análises e grava o código objeto em arquivo texto):

```
java -cp out compilador.Main compilar exemplos/correto.pas exemplos/correto.obj
```

Se o arquivo de saída for omitido, usa o mesmo nome do fonte com extensão `.obj`.
Com `-v` mostra a lista de tokens e a tabela de símbolos.

**Parte 2 – executar** (lê o arquivo de código objeto e executa na máquina hipotética):

```
java -cp out compilador.Main executar exemplos/correto.obj
```

Os comandos `read` (instrução `LEIT`) leem números da entrada padrão, separados por espaço ou quebra de linha. Também aceita entrada redirecionada:

```
printf '1 5\n7 3\n2 2\n10 4\n0 9\n3 8\n6 1\n5 5\n4 12\n9 2\n' | java -cp out compilador.Main executar exemplos/correto.obj
```

**Atalho** – compila e executa em seguida (o `.obj` também é gravado):

```
java -cp out compilador.Main rodar exemplos/expressoes.pas
```

**Testes automáticos** (Linux/macOS/Git Bash): `./testar.sh`

Código de saída: 0 = sucesso, 1 = erro no programa (compilação ou execução), 2 = erro de uso ou arquivo não encontrado.

## Estrutura

```
src/compilador/
  Main.java              linha de comando (compilar / executar / rodar)
  Compilador.java        Parte 1: orquestra léxico -> sintático -> semântico -> gerador
  Lexer.java             analisador léxico (feito à mão)
  Token.java, TokenType.java
  Parser.java            analisador sintático descendente recursivo (1 método por não-terminal)
  Ast.java               árvore sintática abstrata
  SemanticAnalyzer.java  analisador semântico + tabela de símbolos
  CodeGenerator.java     tradutor AST -> código objeto
  VirtualMachine.java    Parte 2: leitor do código objeto e máquina de execução
  CompilerError.java     erros e avisos com linha/coluna
exemplos/                programas de teste (corretos e com erro) e código objeto esperado
gramatica.txt            gramática fornecida
testar.sh                testes automáticos
```

## Como cada fase funciona

1. **Léxico**: percorre o texto caractere a caractere e produz tokens (palavras reservadas, `id`, `numero_real`, símbolos). Inteiros como `10` são `numero_real`. O `.` só faz parte do número se houver dígito depois (`5.` = número 5 e fim de programa). Erros: caractere inválido, número malformado (`12ab`), comentário não fechado.
2. **Sintático**: descendente recursivo preditivo, com 1 token de lookahead e sem retrocesso. Constrói a AST. Pára no primeiro erro, informando o token esperado e o encontrado.
3. **Semântico**: tabela de símbolos com endereço = ordem de declaração. Erros: variável duplicada, variável não declarada (ou usada antes da declaração), divisão por zero literal. Avisos: variável nunca usada; `x := read(y)` com `y` diferente de `x`.
4. **Geração de código**: percorre a AST e grava uma instrução por linha. Rótulos de desvio são endereços absolutos a partir de 0 (resolvidos por backpatching), como no exemplo do professor.

A análise léxica roda por inteiro antes da sintática; por isso, se o arquivo tiver erro léxico e sintático, o léxico é reportado primeiro.

## Código objeto

| Instrução | Efeito |
|---|---|
| `INPP` / `PARA` | início / fim do programa |
| `ALME n` | reserva `n` posições na pilha (iniciam em 0) |
| `CRCT k` | empilha a constante `k` |
| `CRVL n` / `ARMZ n` | empilha `M[n]` / `M[n] := topo` e desempilha |
| `SOMA` `SUBT` `MULT` `DIVI` | operação entre os dois valores do topo |
| `INVE` | inverte o sinal do topo (menos unário) |
| `CPME` `CPMA` `CPIG` `CDES` `CPMI` `CMAI` | `<` `>` `=` `<>` `<=` `>=`; resultado 1 ou 0 |
| `DSVI n` | desvio incondicional |
| `DSVF n` | desvia se o topo for 0 (falso); desempilha |
| `LEIT` / `IMPR` | lê número e empilha / imprime o topo e desempilha |
| `DALM n`, `CONJ`, `DISJ`, `NEGA` | aceitas pela máquina, mas a gramática atual não as gera |

Esquema de tradução do `if`/`while`:

```
if C then A else B $      <C> DSVF Lelse <A> DSVI Lfim  Lelse: <B>  Lfim:
while C do A $            Lini: <C> DSVF Lfim <A> DSVI Lini  Lfim:
```

A máquina valida o arquivo antes de executar (instrução desconhecida, argumento inválido, desvio para endereço inexistente) e, na execução, detecta pilha vazia, divisão por zero, endereço inválido, entrada não numérica e fim da entrada.

## Verificação da gramática

A gramática foi verificada para uso em análise descendente recursiva preditiva:

- **Sem recursão à esquerda** e **sem ambiguidade**: o `else` opcional é resolvido pelo `$` que fecha `if`/`while`.
- **É LL(1)**: nos pares de produções alternativas, os conjuntos FIRST (e FOLLOW, nas produções λ) não se sobrepõem. Por isso 1 token de lookahead basta. Exemplos: `CMDS` escolhe por `write|id`, `if|while`, `real`, ou λ (seguidores `.`, `$`, `else`); `EXP_IDENT` separa `read` de `-|id|numero_real|(`.
- O programa de exemplo (`correto.pas`) é aceito e gera exatamente o código objeto de exemplo.

Pontos que **não são erros**, mas que valem atenção (e como foram tratados):

1. `DC` só é alcançado por `CMDS -> DC`; `PROG` não o usa diretamente. Consequência: declarações podem aparecer em qualquer ponto, inclusive dentro de `if`/`while`. Decisão: o escopo é único (global) e todas as variáveis são alocadas no início do programa (`ALME`), então declarar dentro de laço não realoca memória. Uso antes da declaração continua sendo erro semântico.
2. `MAIS_CMDS -> ; CMDS` não tem λ: **todo** comando simples e toda declaração terminam com `;`, inclusive o último antes de `.`, `$` ou `else`. Já `if`/`while` não levam `;` depois do `$` (`$;` é erro sintático).
3. Só existe o tipo `real`; não há `numero_inteiro`. O exemplo usa `10` e `0`, então o léxico trata qualquer número como `numero_real`.
4. `write` aceita só `id` (não expressão) e `read(id)` só aparece à direita de `:=`. O `id` dentro de `read` não tem efeito na execução (`LEIT` + `ARMZ` no destino); gera aviso se for diferente do destino.
5. O menos unário só aparece no início de um termo (`-a * b`, `2 * (-b)`), não depois de `*` ou `/` (`2 * -b` é erro sintático).
6. O nome do programa (`program Teste`) é lido, mas não é usado.

## Extensões que não estão na gramática

- Comentários `{ ... }` e `// ...` até o fim da linha.
- Palavras reservadas e identificadores sem diferenciar maiúsculas de minúsculas (como em Pascal).
- Mensagens com linha e coluna; avisos além dos erros.

## Exemplos

| Arquivo | O que testa |
|---|---|
| `exemplos/correto.pas` | programa do professor (`while`, `if/else`, `read`, `write`); objeto esperado em `exemplos/esperado/correto.obj` |
| `exemplos/expressoes.pas` | precedência, parênteses, menos unário, números reais (imprime `1.5` e `2.5`) |
| `exemplos/erro_lexico.pas` | caractere inválido `#` |
| `exemplos/erro_sintatico.pas` | `;` faltando |
| `exemplos/erro_semantico.pas` | variável duplicada, não declarada e divisão por zero |
