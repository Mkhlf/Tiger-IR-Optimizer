import antlr_generated.*;
import ir.*;
import ir.datatype.*;
import ir.operand.*;

import java.util.*;
import org.antlr.v4.runtime.tree.*;

/**
 * Tiger to IR code generator using ANTLR visitor pattern
 */
public class TigerIRGenerator extends tigerBaseVisitor<Void> {
    private IRProgram program;
    private IRFunction currentFunction;
    private List<IRInstruction> instructions;
    private Map<String, IRType> symbolTable; // Variable name -> type
    private Map<String, Integer> arrayTable; // Array name -> size
    private List<IRVariableOperand> variables;
    private int tempCounter = 0;
    private int labelCounter = 0;
    private Stack<String> breakLabels; // For break statements in loops
    
    // Track the last computed expression result
    private String lastExprResult = null;
    
    public TigerIRGenerator() {
        this.program = new IRProgram();
        this.symbolTable = new HashMap<>();
        this.arrayTable = new HashMap<>();
        this.breakLabels = new Stack<>();
    }
    
    public IRProgram getProgram() {
        return program;
    }
    
    // Generate a temporary variable name
    private String genTemp() {
        return "t" + (tempCounter++);
    }
    
    // Generate a label name
    private String genLabel(String prefix) {
        return prefix + (labelCounter++);
    }
    
    // Add an instruction to the current function
    private void addInstruction(IRInstruction inst) {
        instructions.add(inst);
    }
    
    // Create an IR instruction
    private IRInstruction createInstruction(IRInstruction.OpCode opCode, String... operandStrings) {
        IROperand[] operands = new IROperand[operandStrings.length];
        for (int i = 0; i < operandStrings.length; i++) {
            String s = operandStrings[i];
            // Check if it's a label
            if (opCode == IRInstruction.OpCode.LABEL || 
                (i == 0 && (opCode == IRInstruction.OpCode.GOTO || 
                            opCode.toString().startsWith("BR")))) {
                operands[i] = new IRLabelOperand(s, null);
            }
            // Check if it's a function name
            else if (i == 0 && (opCode == IRInstruction.OpCode.CALL || 
                               opCode == IRInstruction.OpCode.CALLR)) {
                operands[i] = new IRFunctionOperand(s, null);
            }
            // Check if it's a number constant
            else if (s.matches("-?\\d+")) {
                operands[i] = new IRConstantOperand(IRIntType.get(), s, null);
            } else if (s.matches("-?\\d+\\.\\d+")) {
                operands[i] = new IRConstantOperand(IRFloatType.get(), s, null);
            }
            // Otherwise it's a variable
            else {
                IRType type = symbolTable.getOrDefault(s, IRIntType.get());
                operands[i] = new IRVariableOperand(type, s, null);
            }
        }
        return new IRInstruction(opCode, operands, instructions.size());
    }
    
    @Override
    public Void visitProg(tigerParser.ProgContext ctx) {
        // Initialize main function
        instructions = new ArrayList<>();
        variables = new ArrayList<>();
        currentFunction = new IRFunction("main", null, new ArrayList<>(), variables, instructions);
        
        // Visit declarations
        visit(ctx.decSeg());
        
        // Visit statements
        visit(ctx.statSeq());
        
        // Add return label
        addInstruction(createInstruction(IRInstruction.OpCode.LABEL, "return"));
        
        // Create variable operands for the function
        for (Map.Entry<String, IRType> entry : symbolTable.entrySet()) {
            String varName = entry.getKey();
            IRType type = entry.getValue();
            variables.add(new IRVariableOperand(type, varName, null));
        }
        
        currentFunction.variables = variables;
        currentFunction.instructions = instructions;
        program.functions.add(currentFunction);
        
        return null;
    }
    
    @Override
    public Void visitVarDec(tigerParser.VarDecContext ctx) {
        // Get the type
        IRType type = null;
        boolean isArray = false;
        int arraySize = 0;
        
        
        if (ctx.type().typeId() != null && ctx.type().ARRAY() == null) {
            // Scalar type
            if (ctx.type().typeId().INT() != null) {
                type = IRIntType.get();
            } else {
                type = IRFloatType.get();
            }
        } else {
            // Array type
            isArray = true;
            arraySize = Integer.parseInt(ctx.type().INTLIT().getText());
            if (ctx.type().typeId().INT() != null) {
                type = IRArrayType.get(IRIntType.get(), arraySize);
            } else {
                type = IRArrayType.get(IRFloatType.get(), arraySize);
            }
        }
        
        // Process each variable in the list
        for (TerminalNode idNode : ctx.idList().ID()) {
            String varName = idNode.getText();
            symbolTable.put(varName, type);
            if (isArray) {
                arrayTable.put(varName, arraySize);
            }
            
            // Handle initialization
            if (ctx.optInt() != null && ctx.optInt().const_() != null) {
                String value = ctx.optInt().const_().getText();
                addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, varName, value));
            }
        }
        
        return null;
    }
    
    @Override
    public Void visitFuncDec(tigerParser.FuncDecContext ctx) {
        // For now, skip function declarations
        // Full implementation would create separate IRFunction objects
        return null;
    }
    
    @Override
    public Void visitStat(tigerParser.StatContext ctx) {
        if (ctx.ID() != null && ctx.statIdTail() != null) {
            // Assignment or function call (when there's a statIdTail)
            String id = ctx.ID().getText();
            visitStatWithId(id, ctx.statIdTail());
        } else if (ctx.IF() != null) {
            // If statement
            visit(ctx.expr(0));
            String condition = lastExprResult;
            
            String elseLabel = genLabel("else");
            String endifLabel = genLabel("endif");
            
            // Branch to else if condition is false
            addInstruction(createInstruction(IRInstruction.OpCode.BREQ, elseLabel, condition, "0"));
            
            // Then block
            visit(ctx.statSeq());
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, endifLabel));
            
            // Else block
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, elseLabel));
            if (ctx.ifTail().ELSE() != null) {
                visit(ctx.ifTail().statSeq());
            }
            
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, endifLabel));
            
        } else if (ctx.WHILE() != null) {
            // While loop
            String loopLabel = genLabel("loop");
            String exitLabel = genLabel("exit");
            
            breakLabels.push(exitLabel);
            
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, loopLabel));
            visit(ctx.expr(0));
            String condition = lastExprResult;
            
            addInstruction(createInstruction(IRInstruction.OpCode.BREQ, exitLabel, condition, "0"));
            
            visit(ctx.statSeq());
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, loopLabel));
            
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, exitLabel));
            
            breakLabels.pop();
            
        } else if (ctx.FOR() != null) {
            // For loop
            String loopVar = ctx.ID().getText();
            
            // Initialize loop variable
            visit(ctx.expr(0));
            String startVal = lastExprResult;
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, loopVar, startVal));
            
            // Evaluate end expression
            visit(ctx.expr(1));
            String endVal = lastExprResult;
            
            String loopLabel = genLabel("loop");
            String exitLabel = genLabel("exit");
            
            breakLabels.push(exitLabel);
            
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, loopLabel));
            addInstruction(createInstruction(IRInstruction.OpCode.BRGT, exitLabel, loopVar, endVal));
            
            visit(ctx.statSeq());
            
            addInstruction(createInstruction(IRInstruction.OpCode.ADD, loopVar, loopVar, "1"));
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, loopLabel));
            
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, exitLabel));
            
            breakLabels.pop();
            
        } else if (ctx.BREAK() != null) {
            // Break statement
            if (!breakLabels.isEmpty()) {
                addInstruction(createInstruction(IRInstruction.OpCode.GOTO, breakLabels.peek()));
            }
            
        } else if (ctx.RETURN() != null) {
            // Return statement
            visit(ctx.expr(0));
            // For void functions, just goto return label
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, "return"));
            
        } else if (ctx.LET() != null) {
            // Nested let block
            visit(ctx.decSeg());
            visit(ctx.statSeq());
        }
        
        return null;
    }
    
    private void visitStatWithId(String id, tigerParser.StatIdTailContext ctx) {
        if (ctx.LPAREN() != null) {
            // Function call
            if (id.equals("printi")) {
                visit(ctx.exprList());
                addInstruction(createInstruction(IRInstruction.OpCode.CALL, "puti", lastExprResult));
            } else if (id.equals("printf")) {
                visit(ctx.exprList());
                addInstruction(createInstruction(IRInstruction.OpCode.CALL, "putf", lastExprResult));
            } else if (id.equals("readi")) {
                String temp = genTemp();
                symbolTable.put(temp, IRIntType.get());
                addInstruction(createInstruction(IRInstruction.OpCode.CALLR, temp, "geti"));
                lastExprResult = temp;
            } else if (id.equals("readf")) {
                String temp = genTemp();
                symbolTable.put(temp, IRFloatType.get());
                addInstruction(createInstruction(IRInstruction.OpCode.CALLR, temp, "getf"));
                lastExprResult = temp;
            } else {
                // User-defined function
                // Not fully implemented - would need function signatures
                visit(ctx.exprList());
                addInstruction(createInstruction(IRInstruction.OpCode.CALL, id));
            }
        } else {
            // Assignment
            if (ctx.expr() != null) {
                // Array assignment
                visit(ctx.expr());
                String index = lastExprResult;
                
                visit(ctx.tailValuetail());
                String value = lastExprResult;
                
                addInstruction(createInstruction(IRInstruction.OpCode.ARRAY_STORE, value, id, index));
            } else {
                // Scalar assignment
                visit(ctx.tailValuetail());
                String value = lastExprResult;
                addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, id, value));
            }
        }
    }
    
    @Override
    public Void visitTailValuetail(tigerParser.TailValuetailContext ctx) {
        if (ctx.ID() != null) {
            // ID tailPrime
            String id = ctx.ID().getText();
            visit(ctx.tailPrime());
            // The result is either the function call result or the variable/array access
            if (lastExprResult == null) {
                lastExprResult = id;
            }
        } else if (ctx.const_() != null) {
            // const exprID?
            lastExprResult = ctx.const_().getText();
            if (ctx.exprID() != null) {
                String left = lastExprResult;
                visit(ctx.exprID());
                // exprID will have updated lastExprResult with the binary operation result
            }
        } else if (ctx.LPAREN() != null) {
            // (exprList) exprID?
            visit(ctx.exprList());
            if (ctx.exprID() != null) {
                String left = lastExprResult;
                visit(ctx.exprID());
            }
        }
        return null;
    }
    
    @Override
    public Void visitTailPrime(tigerParser.TailPrimeContext ctx) {
        tigerParser.StatIdTailContext parent = (tigerParser.StatIdTailContext) ctx.parent.parent;
        String id = ((tigerParser.TailValuetailContext) ctx.parent).ID().getText();
        
        if (ctx.LPAREN() != null) {
            // Function call
            if (id.equals("readi")) {
                String temp = genTemp();
                symbolTable.put(temp, IRIntType.get());
                addInstruction(createInstruction(IRInstruction.OpCode.CALLR, temp, "geti"));
                lastExprResult = temp;
            } else if (id.equals("readf")) {
                String temp = genTemp();
                symbolTable.put(temp, IRFloatType.get());
                addInstruction(createInstruction(IRInstruction.OpCode.CALLR, temp, "getf"));
                lastExprResult = temp;
            } else {
                // User function - would need to know return type
                visit(ctx.exprList());
                String temp = genTemp();
                symbolTable.put(temp, IRIntType.get()); // Default to int
                addInstruction(createInstruction(IRInstruction.OpCode.CALLR, temp, id));
                lastExprResult = temp;
            }
        } else if (ctx.expr() != null) {
            // Array access
            visit(ctx.expr());
            String index = lastExprResult;
            String temp = genTemp();
            // Determine array element type
            IRType arrayType = symbolTable.get(id);
            IRType elemType = IRIntType.get(); // Default
            if (arrayType instanceof IRArrayType) {
                elemType = ((IRArrayType) arrayType).getElementType();
            }
            symbolTable.put(temp, elemType);
            addInstruction(createInstruction(IRInstruction.OpCode.ARRAY_LOAD, temp, id, index));
            lastExprResult = temp;
            
            if (ctx.exprID() != null) {
                String left = lastExprResult;
                visit(ctx.exprID());
            }
        } else {
            // Just a variable reference
            lastExprResult = id;
            if (ctx.exprID() != null) {
                String left = lastExprResult;
                visit(ctx.exprID());
            }
        }
        return null;
    }
    
    @Override
    public Void visitExprID(tigerParser.ExprIDContext ctx) {
        String left = lastExprResult;
        visit(ctx.expr());
        String right = lastExprResult;
        String temp = genTemp();
        symbolTable.put(temp, IRIntType.get()); // Most operations produce int
        
        if (ctx.OR() != null) {
            addInstruction(createInstruction(IRInstruction.OpCode.OR, temp, left, right));
        } else if (ctx.AND() != null) {
            addInstruction(createInstruction(IRInstruction.OpCode.AND, temp, left, right));
        } else if (ctx.LTE() != null) {
            // a <= b becomes !(a > b)
            String gtTemp = genTemp();
            symbolTable.put(gtTemp, IRIntType.get());
            addInstruction(createInstruction(IRInstruction.OpCode.BRGT, genLabel("true"), left, right));
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, gtTemp, "0"));
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, genLabel("end")));
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, "true" + (labelCounter-2)));
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, gtTemp, "1"));
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, "end" + (labelCounter-1)));
            addInstruction(createInstruction(IRInstruction.OpCode.SUB, temp, "1", gtTemp));
        } else if (ctx.GTE() != null) {
            // a >= b becomes !(a < b)
            String ltTemp = genTemp();
            symbolTable.put(ltTemp, IRIntType.get());
            addInstruction(createInstruction(IRInstruction.OpCode.BRLT, genLabel("true"), left, right));
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, ltTemp, "0"));
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, genLabel("end")));
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, "true" + (labelCounter-2)));
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, ltTemp, "1"));
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, "end" + (labelCounter-1)));
            addInstruction(createInstruction(IRInstruction.OpCode.SUB, temp, "1", ltTemp));
        } else if (ctx.LT() != null || ctx.GT() != null || ctx.NEQ() != null || ctx.EQ() != null) {
            // For comparison operations, generate boolean result
            String trueLabel = genLabel("true");
            String endLabel = genLabel("end");
            
            if (ctx.LT() != null) {
                addInstruction(createInstruction(IRInstruction.OpCode.BRLT, trueLabel, left, right));
            } else if (ctx.GT() != null) {
                addInstruction(createInstruction(IRInstruction.OpCode.BRGT, trueLabel, left, right));
            } else if (ctx.NEQ() != null) {
                addInstruction(createInstruction(IRInstruction.OpCode.BRNEQ, trueLabel, left, right));
            } else { // EQ
                addInstruction(createInstruction(IRInstruction.OpCode.BREQ, trueLabel, left, right));
            }
            
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "0"));
            addInstruction(createInstruction(IRInstruction.OpCode.GOTO, endLabel));
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, trueLabel));
            addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "1"));
            addInstruction(createInstruction(IRInstruction.OpCode.LABEL, endLabel));
        } else if (ctx.PLUS() != null) {
            addInstruction(createInstruction(IRInstruction.OpCode.ADD, temp, left, right));
        } else if (ctx.MINUS() != null) {
            addInstruction(createInstruction(IRInstruction.OpCode.SUB, temp, left, right));
        } else if (ctx.MULT() != null) {
            addInstruction(createInstruction(IRInstruction.OpCode.MULT, temp, left, right));
        } else if (ctx.DIV() != null) {
            addInstruction(createInstruction(IRInstruction.OpCode.DIV, temp, left, right));
        }
        
        lastExprResult = temp;
        return null;
    }
    
    // Expression visitors that follow the grammar structure
    @Override
    public Void visitExpr(tigerParser.ExprContext ctx) {
        visit(ctx.orExpr());
        return null;
    }
    
    @Override  
    public Void visitOrExpr(tigerParser.OrExprContext ctx) {
        visit(ctx.andExpr());
        if (ctx.orExpr() != null) {
            String left = lastExprResult;
            visit(ctx.orExpr());
            String right = lastExprResult;
            String temp = genTemp();
            symbolTable.put(temp, IRIntType.get());
            addInstruction(createInstruction(IRInstruction.OpCode.OR, temp, left, right));
            lastExprResult = temp;
        }
        return null;
    }
    
    @Override
    public Void visitAndExpr(tigerParser.AndExprContext ctx) {
        visit(ctx.lteExpr());
        if (ctx.andExpr() != null) {
            String left = lastExprResult;
            visit(ctx.andExpr());
            String right = lastExprResult;
            String temp = genTemp();
            symbolTable.put(temp, IRIntType.get());
            addInstruction(createInstruction(IRInstruction.OpCode.AND, temp, left, right));
            lastExprResult = temp;
        }
        return null;
    }
    
    // Continue with other expression rules...
    @Override
    public Void visitLteExpr(tigerParser.LteExprContext ctx) {
        visit(ctx.gteExpr());
        if (ctx.lteExpr() != null) {
            String left = lastExprResult;
            visit(ctx.lteExpr());
            String right = lastExprResult;
            generateComparison("<=", left, right);
        }
        return null;
    }
    
    @Override
    public Void visitGteExpr(tigerParser.GteExprContext ctx) {
        visit(ctx.ltExpr());
        if (ctx.gteExpr() != null) {
            String left = lastExprResult;
            visit(ctx.gteExpr());
            String right = lastExprResult;
            generateComparison(">=", left, right);
        }
        return null;
    }
    
    @Override
    public Void visitLtExpr(tigerParser.LtExprContext ctx) {
        visit(ctx.gtExpr());
        if (ctx.ltExpr() != null) {
            String left = lastExprResult;
            visit(ctx.ltExpr());
            String right = lastExprResult;
            generateComparison("<", left, right);
        }
        return null;
    }
    
    @Override
    public Void visitGtExpr(tigerParser.GtExprContext ctx) {
        visit(ctx.neqExpr());
        if (ctx.gtExpr() != null) {
            String left = lastExprResult;
            visit(ctx.gtExpr());
            String right = lastExprResult;
            generateComparison(">", left, right);
        }
        return null;
    }
    
    @Override
    public Void visitNeqExpr(tigerParser.NeqExprContext ctx) {
        visit(ctx.eqExpr());
        if (ctx.neqExpr() != null) {
            String left = lastExprResult;
            visit(ctx.neqExpr());
            String right = lastExprResult;
            generateComparison("!=", left, right);
        }
        return null;
    }
    
    @Override
    public Void visitEqExpr(tigerParser.EqExprContext ctx) {
        visit(ctx.addsubExpr());
        if (ctx.eqExpr() != null) {
            String left = lastExprResult;
            visit(ctx.eqExpr());
            String right = lastExprResult;
            generateComparison("==", left, right);
        }
        return null;
    }
    
    private void generateComparison(String op, String left, String right) {
        String temp = genTemp();
        symbolTable.put(temp, IRIntType.get());
        String trueLabel = genLabel("true");
        String endLabel = genLabel("end");
        
        IRInstruction.OpCode branchOp = null;
        switch (op) {
            case "<": branchOp = IRInstruction.OpCode.BRLT; break;
            case ">": branchOp = IRInstruction.OpCode.BRGT; break;
            case "<=": 
                // For <=, use NOT(>) logic
                addInstruction(createInstruction(IRInstruction.OpCode.BRGT, endLabel, left, right));
                addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "1"));
                addInstruction(createInstruction(IRInstruction.OpCode.GOTO, trueLabel));
                addInstruction(createInstruction(IRInstruction.OpCode.LABEL, endLabel));
                addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "0"));
                addInstruction(createInstruction(IRInstruction.OpCode.LABEL, trueLabel));
                lastExprResult = temp;
                return;
            case ">=":
                // For >=, use NOT(<) logic  
                addInstruction(createInstruction(IRInstruction.OpCode.BRLT, endLabel, left, right));
                addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "1"));
                addInstruction(createInstruction(IRInstruction.OpCode.GOTO, trueLabel));
                addInstruction(createInstruction(IRInstruction.OpCode.LABEL, endLabel));
                addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "0"));
                addInstruction(createInstruction(IRInstruction.OpCode.LABEL, trueLabel));
                lastExprResult = temp;
                return;
            case "!=": branchOp = IRInstruction.OpCode.BRNEQ; break;
            case "==": branchOp = IRInstruction.OpCode.BREQ; break;
        }
        
        addInstruction(createInstruction(branchOp, trueLabel, left, right));
        addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "0"));
        addInstruction(createInstruction(IRInstruction.OpCode.GOTO, endLabel));
        addInstruction(createInstruction(IRInstruction.OpCode.LABEL, trueLabel));
        addInstruction(createInstruction(IRInstruction.OpCode.ASSIGN, temp, "1"));
        addInstruction(createInstruction(IRInstruction.OpCode.LABEL, endLabel));
        
        lastExprResult = temp;
    }
    
    @Override
    public Void visitAddsubExpr(tigerParser.AddsubExprContext ctx) {
        visit(ctx.multdivExpr());
        if (ctx.addsubExpr() != null) {
            String left = lastExprResult;
            visit(ctx.addsubExpr());
            String right = lastExprResult;
            String temp = genTemp();
            
            // Determine result type
            IRType leftType = symbolTable.getOrDefault(left, IRIntType.get());
            IRType rightType = symbolTable.getOrDefault(right, IRIntType.get());
            IRType resultType = (leftType == IRFloatType.get() || rightType == IRFloatType.get()) 
                               ? IRFloatType.get() : IRIntType.get();
            symbolTable.put(temp, resultType);
            
            if (ctx.PLUS() != null) {
                addInstruction(createInstruction(IRInstruction.OpCode.ADD, temp, left, right));
            } else {
                addInstruction(createInstruction(IRInstruction.OpCode.SUB, temp, left, right));
            }
            lastExprResult = temp;
        }
        return null;
    }
    
    @Override
    public Void visitMultdivExpr(tigerParser.MultdivExprContext ctx) {
        visit(ctx.unaryExpr());
        if (ctx.multdivExpr() != null) {
            String left = lastExprResult;
            visit(ctx.multdivExpr());
            String right = lastExprResult;
            String temp = genTemp();
            
            // Determine result type  
            IRType leftType = symbolTable.getOrDefault(left, IRIntType.get());
            IRType rightType = symbolTable.getOrDefault(right, IRIntType.get());
            IRType resultType = (leftType == IRFloatType.get() || rightType == IRFloatType.get()) 
                               ? IRFloatType.get() : IRIntType.get();
            symbolTable.put(temp, resultType);
            
            if (ctx.MULT() != null) {
                addInstruction(createInstruction(IRInstruction.OpCode.MULT, temp, left, right));
            } else {
                addInstruction(createInstruction(IRInstruction.OpCode.DIV, temp, left, right));
            }
            lastExprResult = temp;
        }
        return null;
    }
    
    @Override
    public Void visitUnaryExpr(tigerParser.UnaryExprContext ctx) {
        if (ctx.MINUS() != null) {
            visit(ctx.unaryExpr());
            String operand = lastExprResult;
            String temp = genTemp();
            IRType type = symbolTable.getOrDefault(operand, IRIntType.get());
            symbolTable.put(temp, type);
            addInstruction(createInstruction(IRInstruction.OpCode.SUB, temp, "0", operand));
            lastExprResult = temp;
        } else {
            visit(ctx.primaryExpr());
        }
        return null;
    }
    
    @Override
    public Void visitPrimaryExpr(tigerParser.PrimaryExprContext ctx) {
        if (ctx.const_() != null) {
            lastExprResult = ctx.const_().getText();
        } else if (ctx.lvalue() != null) {
            visit(ctx.lvalue());
        } else if (ctx.LPAREN() != null) {
            visit(ctx.expr());
        }
        return null;
    }
    
    @Override
    public Void visitLvalue(tigerParser.LvalueContext ctx) {
        String id = ctx.ID().getText();
        if (ctx.lvalueTail().expr() != null) {
            // Array access
            visit(ctx.lvalueTail().expr());
            String index = lastExprResult;
            String temp = genTemp();
            
            // Get element type
            IRType arrayType = symbolTable.get(id);
            IRType elemType = IRIntType.get();
            if (arrayType instanceof IRArrayType) {
                elemType = ((IRArrayType) arrayType).getElementType();
            }
            symbolTable.put(temp, elemType);
            
            addInstruction(createInstruction(IRInstruction.OpCode.ARRAY_LOAD, temp, id, index));
            lastExprResult = temp;
        } else {
            // Simple variable
            lastExprResult = id;
        }
        return null;
    }
    
    @Override
    public Void visitExprList(tigerParser.ExprListContext ctx) {
        if (ctx.expr() != null) {
            visit(ctx.expr());
            // For now, we only handle single-argument functions
            // Full implementation would collect all arguments
        }
        return null;
    }
}
