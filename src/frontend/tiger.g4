grammar tiger;

@header {
    package antlr_generated;
}

// Parser Rules
prog : MAIN LET decSeg IN BEGIN statSeq END;

decSeg : varDecList funcDecList;

varDecList : (varDec varDecList)? ;  // optional and multiple declarations
varDec : VAR idList COLON type optInt SEMICOLON;

funcDecList : (funcDec funcDecList)? ;  // optional and multiple functions
funcDec : FUNCTION ID LPAREN paramList RPAREN retType BEGIN statSeq END;

// type
type : typeId | ARRAY LBRACK INTLIT RBRACK OF typeId;
typeId : INT | FLOAT;

// id-list
idList : ID (COMMA ID)*;

// optional init
optInt : (ASSIGN const)?;

// parameter list
paramList : (param paramListTail)?;
paramListTail : (COMMA param paramListTail)?;

// return type
retType : (COLON type)?;

// param
param : ID COLON type;

// statement sequence
statSeq : stat (statSeq)?;

// statement
stat : ID statIdTail
     | IF expr THEN statSeq ifTail
     | WHILE expr DO statSeq ENDDO SEMICOLON
     | FOR ID ASSIGN expr TO expr DO statSeq ENDDO SEMICOLON
     | BREAK SEMICOLON
     | RETURN expr SEMICOLON
     | LET decSeg IN statSeq END;

statIdTail: LPAREN exprList RPAREN SEMICOLON
            |(LBRACK expr RBRACK)? ASSIGN tailValuetail;

tailValuetail: ID tailPrime
              |const exprID? SEMICOLON
              |(LPAREN exprList RPAREN) exprID? SEMICOLON;

tailPrime : (LBRACK expr RBRACK)? exprID? SEMICOLON
        |LPAREN exprList RPAREN SEMICOLON;

exprID: (OR expr)
        |(AND expr)
        |(LTE expr)
        |(GTE expr)
        |(LT expr)
        |(GT expr)
        |(NEQ expr)
        |(EQ expr)
        |((PLUS | MINUS) expr)
        |((MULT|DIV) expr);


ifTail : ENDIF SEMICOLON
       | ELSE statSeq ENDIF SEMICOLON;

expr : orExpr;

orExpr          :  andExpr (OR orExpr)?;
andExpr         :  lteExpr (AND andExpr)?;
lteExpr         :  gteExpr (LTE lteExpr)?;
gteExpr         :  ltExpr (GTE gteExpr)?;
ltExpr          :  gtExpr (LT ltExpr)?;
gtExpr          :  neqExpr (GT gtExpr)?;
neqExpr         :  eqExpr (NEQ neqExpr)?;
eqExpr          :  addsubExpr (EQ eqExpr)?;
addsubExpr         :  multdivExpr ((PLUS | MINUS) addsubExpr)?;
multdivExpr        :  unaryExpr ((MULT|DIV) multdivExpr)?;
unaryExpr       :  primaryExpr | MINUS unaryExpr;

primaryExpr     : const | lvalue | LPAREN expr RPAREN;
// cosnt 
const : INTLIT | FLOATLIT;

// expression list
exprList : (expr exprListTail)?;
exprListTail : (COMMA expr exprListTail)?;

// lvalue
lvalue : ID lvalueTail;
lvalueTail : (LBRACK expr RBRACK)?;


/*
 * Lexer Rules
 * all upper case -> lexer
 */

fragment LOWERCASE : [a-z];
fragment UPPERCASE : [A-Z];
fragment DIGIT : [0-9];
fragment LETTER : [a-zA-Z];
fragment WHITESPACE : [ \t\n\r];

COMMENT : '/*' .*? '*/' -> skip;

MAIN : 'main';
ARRAY : 'array';
BREAK : 'break';
DO : 'do';
IF : 'if';
ELSE : 'else';
FOR : 'for';
FUNCTION : 'function';
LET : 'let';
IN : 'in';
OF : 'of';
THEN : 'then';
TO : 'to';
VAR : 'var';
WHILE : 'while';
ENDIF : 'endif';
BEGIN : 'begin';
END : 'end';
ENDDO : 'enddo';
RETURN : 'return';
INT : 'int';
FLOAT : 'float';

ID : LETTER (LETTER | DIGIT | '_')*;

INTLIT : DIGIT+;
FLOATLIT : DIGIT+ '.' DIGIT*;

COMMA : ',';
COLON : ':';
SEMICOLON : ';';
LPAREN : '(';
RPAREN : ')';
LBRACK : '[';
RBRACK : ']';

PLUS : '+';
MINUS : '-';
MULT : '*';
DIV : '/';
EQ : '=';
NEQ : '<>';
LT : '<';
GT : '>';
LTE : '<=';
GTE : '>=';
AND : '&';
OR : '|';

ASSIGN : ':=';

WS : WHITESPACE+ -> skip;
