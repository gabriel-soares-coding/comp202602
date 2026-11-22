package compilador;

import java.util.ArrayList;
import java.util.List;

import compilador.Ast.*;

/**
 * Analisador sintático descendente recursivo preditivo (LL(1)).
 * Cada não-terminal da gramática é um método com o mesmo nome (em minúsculas).
 * Não há retrocesso: o próximo token (lookahead) decide a produção.
 * Pára no primeiro erro sintático.
 */
public class Parser {
    private final List<Token> tokens;
    private int i = 0;

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    // ---------- utilitários ----------

    private Token cur() { return tokens.get(i); }
    private TokenType la() { return cur().type(); }

    private Token advance() {
        Token t = tokens.get(i);
        if (t.type() != TokenType.EOF) i++;
        return t;
    }

    private static String describe(Token t) {
        return t.type() == TokenType.EOF ? "fim de arquivo" : "'" + t.lexeme() + "'";
    }

    private CompilerError error(String msg) {
        Token t = cur();
        return new CompilerError("erro sintático", msg, t.line(), t.col());
    }

    private Token expect(TokenType type) {
        if (la() == type) return advance();
        throw error("esperado " + type.display() + ", encontrado " + describe(cur()));
    }

    // ---------- gramática ----------

    /** PROG -> program id CMDS . */
    public Program parse() {
        expect(TokenType.PROGRAM);
        Token name = expect(TokenType.ID);
        List<Stmt> body = new ArrayList<>();
        cmds(body);
        expect(TokenType.DOT);
        expect(TokenType.EOF);
        return new Program(name, body);
    }

    /** CMDS -> CMD MAIS_CMDS | CMD_COND CMDS | DC | λ */
    private void cmds(List<Stmt> out) {
        switch (la()) {
            case WRITE, ID -> {
                out.add(cmd());
                maisCmds(out);
            }
            case IF, WHILE -> {
                out.add(cmdCond());
                cmds(out);
            }
            case REAL -> dc(out);
            case DOT, DOLLAR, ELSE -> { /* λ: seguidores de CMDS */ }
            default -> throw error("esperado comando (write, identificador, if, while, real) "
                    + "ou fim de bloco ('.', '$', 'else'), encontrado " + describe(cur()));
        }
    }

    /** MAIS_CMDS -> ; CMDS */
    private void maisCmds(List<Stmt> out) {
        expect(TokenType.SEMI);
        cmds(out);
    }

    /** DC -> VAR MAIS_CMDS */
    private void dc(List<Stmt> out) {
        out.add(var());
        maisCmds(out);
    }

    /** VAR -> TIPO : VARS     TIPO -> real */
    private VarDecl var() {
        Token type = expect(TokenType.REAL);
        expect(TokenType.COLON);
        List<Token> names = new ArrayList<>();
        vars(names);
        return new VarDecl(type, names);
    }

    /** VARS -> id MAIS_VAR */
    private void vars(List<Token> names) {
        names.add(expect(TokenType.ID));
        maisVar(names);
    }

    /** MAIS_VAR -> , VARS | λ */
    private void maisVar(List<Token> names) {
        if (la() == TokenType.COMMA) {
            advance();
            vars(names);
        }
    }

    /** CMD_COND -> if CONDICAO then CMDS PFALSA $ | while CONDICAO do CMDS $ */
    private Stmt cmdCond() {
        if (la() == TokenType.IF) {
            advance();
            Cond c = condicao();
            expect(TokenType.THEN);
            List<Stmt> thenPart = new ArrayList<>();
            cmds(thenPart);
            List<Stmt> elsePart = pfalsa();
            expect(TokenType.DOLLAR);
            return new If(c, thenPart, elsePart);
        }
        expect(TokenType.WHILE);
        Cond c = condicao();
        expect(TokenType.DO);
        List<Stmt> body = new ArrayList<>();
        cmds(body);
        expect(TokenType.DOLLAR);
        return new While(c, body);
    }

    /** PFALSA -> else CMDS | λ   (devolve null quando não há else) */
    private List<Stmt> pfalsa() {
        if (la() == TokenType.ELSE) {
            advance();
            List<Stmt> list = new ArrayList<>();
            cmds(list);
            return list;
        }
        return null;
    }

    /** CMD -> write ( id ) | id RESTO_IDENT */
    private Stmt cmd() {
        if (la() == TokenType.WRITE) {
            advance();
            expect(TokenType.LPAREN);
            Token id = expect(TokenType.ID);
            expect(TokenType.RPAREN);
            return new Write(id);
        }
        Token id = expect(TokenType.ID);
        return restoIdent(id);
    }

    /** RESTO_IDENT -> := EXP_IDENT */
    private Stmt restoIdent(Token id) {
        expect(TokenType.ASSIGN);
        return new Assign(id, expIdent());
    }

    /** EXP_IDENT -> EXPRESSAO | read ( id ) */
    private Expr expIdent() {
        if (la() == TokenType.READ) {
            advance();
            expect(TokenType.LPAREN);
            Token id = expect(TokenType.ID);
            expect(TokenType.RPAREN);
            return new Read(id);
        }
        return expressao();
    }

    /** CONDICAO -> EXPRESSAO RELACAO EXPRESSAO */
    private Cond condicao() {
        Expr left = expressao();
        Token op = relacao();
        Expr right = expressao();
        return new Cond(left, op, right);
    }

    /** RELACAO -> = | <> | >= | <= | > | < */
    private Token relacao() {
        switch (la()) {
            case EQ, NE, GE, LE, GT, LT:
                return advance();
            default:
                throw error("esperado operador relacional (=, <>, >=, <=, >, <), encontrado " + describe(cur()));
        }
    }

    /** EXPRESSAO -> TERMO OUTROS_TERMOS */
    private Expr expressao() {
        return outrosTermos(termo());
    }

    /** OUTROS_TERMOS -> OP_AD TERMO OUTROS_TERMOS | λ   (associativo à esquerda) */
    private Expr outrosTermos(Expr left) {
        if (la() == TokenType.PLUS || la() == TokenType.MINUS) {
            Token op = advance(); // OP_AD
            Expr right = termo();
            return outrosTermos(new Bin(op, left, right));
        }
        return left;
    }

    /** TERMO -> OP_UN FATOR MAIS_FATORES */
    private Expr termo() {
        Token un = opUn();
        Expr f = fator();
        if (un != null) f = new Neg(un, f);
        return maisFatores(f);
    }

    /** OP_UN -> - | λ */
    private Token opUn() {
        return la() == TokenType.MINUS ? advance() : null;
    }

    /** MAIS_FATORES -> OP_MUL FATOR MAIS_FATORES | λ   (associativo à esquerda) */
    private Expr maisFatores(Expr left) {
        if (la() == TokenType.STAR || la() == TokenType.SLASH) {
            Token op = advance(); // OP_MUL
            Expr right = fator();
            return maisFatores(new Bin(op, left, right));
        }
        return left;
    }

    /** FATOR -> id | numero_real | ( EXPRESSAO ) */
    private Expr fator() {
        switch (la()) {
            case ID:
                return new Var(advance());
            case NUM:
                return new Num(advance());
            case LPAREN: {
                advance();
                Expr e = expressao();
                expect(TokenType.RPAREN);
                return e;
            }
            default:
                throw error("esperado expressão (identificador, número ou '('), encontrado " + describe(cur()));
        }
    }
}
