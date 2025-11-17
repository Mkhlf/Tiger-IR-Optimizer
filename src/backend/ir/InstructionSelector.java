package ir;

import ir.operand.*;
import java.util.*;
import ir.datatype.*;

// ADD, ADDI, SUB, MUL, DIV, AND, ANDI, OR, ORI, SLL, // bin ops
// LI, LW, MOVE, SW, LA, // data movement
// BEQ, BNE, BLT, BGT, BGE, // branches
// J, JAL, JR, // jumps


public class InstructionSelector {
    private List < String > g_mipsInstructions; // global mips instructions
    private List < String > i_mipsInstructions; // instructions for a single IR instruction
    private List < String > dataSegment; // data segment
    private IRProgram program;
    private IRFunction currFunction;
    private Map < IRVariableOperand, String > registerMap;
    private Map<String, Integer> varToStackMap; // save the offset of the variables in the stack 

    public InstructionSelector(IRProgram pr) {
        g_mipsInstructions = new ArrayList < > ();
        dataSegment = new ArrayList < > ();
        program = pr;
    }

    public List<String> selectInstructions() {
        dataSegment.add(".data");

        // handle data segment
        for (IRFunction function: program.functions) {
            for (IRVariableOperand var: function.getVarOnly()) {
                if (var.getType() instanceof IRArrayType) {
                    dataSegment.add(function+var.toString() + ": .space " + ((IRArrayType) var.getType()).getSize());
                } else {
                    // dataSegment.add(function+"_"+var.toString() + ": .word 0");
                }
            }
        }

        g_mipsInstructions.addAll(dataSegment);
        g_mipsInstructions.add(".text");

        // handle the text segment
        // pupulate the main function first

        for (IRFunction function : program.functions) {
            if (function.name.equals("main")){
            currFunction = function;
            registerMap = new HashMap < > ();
            varToStackMap = new HashMap < > ();
            
            // map the parameters to the registers
            for (IRVariableOperand param: function.parameters) {
                registerMap.put(param, "a" + function.parameters.indexOf(param));
            }

            // dump map to err stream
            System.err.println("Register Map:  " + function.name);
            for (Map.Entry < IRVariableOperand, String > entry: registerMap.entrySet()) {
                System.err.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
            }

            // select instructions for the main function
            g_mipsInstructions.addAll(selectInstructionsForFunction(function));
            }
        }

        // populate the rest of the functions
        for (IRFunction function : program.functions) {
            if (!function.name.equals("main")){
            currFunction = function;
            registerMap = new HashMap < > ();
            varToStackMap = new HashMap < > ();
            
            for (IRVariableOperand param: function.parameters) {
                registerMap.put(param, "a" + function.parameters.indexOf(param));
            }

            // dump map 
            System.err.println("Register Map:  " + function.name);
            for (Map.Entry < IRVariableOperand, String > entry: registerMap.entrySet()) {
                System.err.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
            }

            g_mipsInstructions.addAll(selectInstructionsForFunction(function));
            }
        }

        return g_mipsInstructions;
    }

    // the following two functions returns the register to be used for writing/reading the value of the variable
    // if the variable is a parameter, it returns the 'a' register
    // if the variable is in the stack, it loads the value from the stack to a temp register and returns the temp register
    // if it was an array in the data segment, it returns the label of the array
    // if it is a constant or a label, it returns the value as is 


    private String getRegForWriting(IROperand var, String tempReg) {
        System.err.println("Function: " + currFunction.name);
        System.err.println("--- Var: " + var.toString());
        if (var instanceof IRVariableOperand) {
            // if it is a parameter, return the 'a' register
            if (registerMap.containsKey(var)) {
                System.err.println(" --- --- Var in map");
                return registerMap.get(var);
            }
            // if it is in the stack, load the value from the stack to a temp register and return the temp register
            if (varToStackMap.containsKey(var.toString())) {
                // load the value from the stack to a temp register
                return tempReg;
            }
        }

        if (currFunction.variables.contains(var)) {
            return currFunction+var.toString();
        }

        return var.toString();
    }
    
    private String getRegForReading(IROperand var, String tempReg) {
        System.err.println("Function: " + currFunction.name);
        System.err.println("--- Var: " + var.toString());
        if (var instanceof IRVariableOperand) {
            // if it is a parameter, return the 'a' register
            if (registerMap.containsKey(var)) {
                System.err.println(" --- --- Var in map");
                return registerMap.get(var);
            }
            // if it is in the stack, load the value from the stack to a temp register and return the temp register
            if (varToStackMap.containsKey(var.toString())) {
                // load the value from the stack to a temp register
                i_mipsInstructions.add("lw $" + tempReg + ", " + varToStackMap.get(var.toString()) + "($sp)");
                return tempReg;
            }
        }

        if (currFunction.variables.contains(var)) {
            return currFunction+var.toString();
        }

        return var.toString();
    }

    // save the value back to the stack if it is a variable from the stack
    private void saveToStack(IROperand var, String reg) {
        if (var instanceof IRVariableOperand) {
            // Save it back to the stack only if it is a variable -- not a parameter
            if (varToStackMap.containsKey(var.toString())) {
                i_mipsInstructions.add("sw $" + reg + ", " + varToStackMap.get(var.toString()) + "($sp)");
            }
        }
    }
    
    private List<String> selectInstructionsForFunction(IRFunction function) {
        List<String> mipsI = new ArrayList<>();
        mipsI.add(function.name + ":");


        mipsI.addAll(getPrologue(function));

        for (IRVariableOperand var: function.getVarOnly()) {
            // store the variables in the stack - except arrays
            if (!(var.getType() instanceof IRArrayType)) {
                int loc = varToStackMap.size()*4 + 8;
                varToStackMap.put(var.toString(), loc);
                mipsI.add("li $t0, 0");
                mipsI.add("sw $t0, " + loc + "($sp)");
            }
        }

        for (IRInstruction instruction : function.instructions) {
            selectInstructionsForInstruction(instruction);
            mipsI.addAll(i_mipsInstructions);
        }

        // epilogue -- restore values and then return

        mipsI.addAll(getEpilogue(function));
        
        return mipsI;
    }

    private List<String> getPrologue(IRFunction function) {
        List<String> prologue = new ArrayList<>();
        int stSize = 8 + function.getVarOnly().size() * 4 + function.parameters.size() * 4;
        if (stSize % 8 != 0) {
            stSize += 4;
        }
        prologue.add("addi $sp, $sp, -" + stSize);
        prologue.add("sw $ra, 0($sp)");
        prologue.add("sw $fp, 4($sp)");
        prologue.add("addi $fp, $sp, " + (stSize - 4));
        return prologue;
    }

    private List<String> getEpilogue(IRFunction function) {
        List<String> epilogue = new ArrayList<>();
        int stSize = 8 + function.getVarOnly().size() * 4 + function.parameters.size() * 4;
        if (stSize % 8 != 0) {
            stSize += 4;
        }
        epilogue.add("lw $fp, 4($sp)");
        epilogue.add("lw $ra, 0($sp)");
        epilogue.add("addi $sp, $sp, " + stSize);
        if (function.name.equals("main")) {
            epilogue.add("li $v0, 10");
            epilogue.add("syscall");
        } else{
            epilogue.add("jr $ra");
        }
        return epilogue;
    }

    private List<String> saveVarPar(IRFunction function) {
        List<String> saveVarPar = new ArrayList<>();
        // the first 8 bytes are for the return address and the frame pointer -- already saved in the prologue
        // the next n * 4 bytes are for the variables -- already saved whenever they are assigned
        // the next m * 4 bytes are for the parameters -- save them to the stack

        int n = varToStackMap.size();
        for (int i = 0; i < function.parameters.size(); i++) {
            int offset = (n * 4) + 8 + (i * 4);
            String inst = "sw $a" + i + ", " + offset + "($sp)";
            saveVarPar.add(inst);
        }
        return saveVarPar;
    }

    private List<String> restoreVarPar(IRFunction function) {
        List<String> restoreVarPar = new ArrayList<>();
        // the first 8 bytes are for the return address and the frame pointer -- already restored in the epilogue
        // the next n * 4 bytes are for the variables -- will be restored whenever they are used
        // the next m * 4 bytes are for the parameters -- restore them from the stack

        int n = varToStackMap.size();
        for (int i = 0; i < function.parameters.size(); i++) {
            int offset = (n * 4) + 8 + (i * 4);
            String inst = "lw $a" + i + ", " + offset + "($sp)";
            restoreVarPar.add(inst);
        }
        
        return restoreVarPar;
    }

    private void selectInstructionsForInstruction(IRInstruction instruction) {
        i_mipsInstructions = new ArrayList<>();
        i_mipsInstructions.add("# " + instruction.toString());

        switch (instruction.opCode) {
            case ASSIGN:
                assign_h(instruction);
                break;
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
                if (instruction.operands[1] instanceof IRVariableOperand && instruction.operands[2] instanceof IRVariableOperand) {
                    binaryOpsVar_h(instruction);
                } else {
                    binaryOpsIm_h(instruction);
                }
                break;
            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRGEQ:
            case BRLEQ:
                branch_h(instruction);
                break;
            case GOTO:
                goto_h(instruction);
                break;
            case RETURN:
                return_h(instruction);
                break;
            case CALL:
            case CALLR:
                call_h(instruction);
                break;
            case ARRAY_STORE:
            case ARRAY_LOAD:
                array_h(instruction);
                break;
            case LABEL:
                i_mipsInstructions.add(currFunction + "_" + instruction.operands[0].toString() + ":");
                break;
            default:
                break;
        }
    }

    private void assign_h (IRInstruction instruction) {
        // normal assignment
        if (instruction.operands.length == 2){
        String dest = getRegForWriting(instruction.operands[0], "t1"); // either a variable or a parameter 
        String src = getRegForReading(instruction.operands[1], "t0"); // either a variable, a parameter, or a constant
        if (instruction.operands[1] instanceof IRConstantOperand){
            // load immediate and then store it in the stack location of the variable
            i_mipsInstructions.add("li $" + dest + ", " + src);

            // i_mipsInstructions.add("li $" + dest + ", " + src); 
        }else {
            i_mipsInstructions.add("move $" + dest + ", $" + src); 
        }
        // save the value back to the stack if it is a variable
        saveToStack(instruction.operands[0], dest);
        }
        else
        {
            // array assignment
            // assign A, x, value 
            // makes the first x elements of A to be 'value'
            // A (an address that needs to be loaded, or already loaded if it was a paramater) is an array, x and value are (variables, parameters, or constants)

            String src = getRegForReading(instruction.operands[0], "t7"); //either the array from the stack or a parameter
            String N = getRegForReading(instruction.operands[1], "t8"); // either a variable, a parameter, or a constant
            String val = getRegForReading(instruction.operands[2] , "t9");

            if (instruction.operands[1] instanceof IRConstantOperand){
                i_mipsInstructions.add("li $t8, " + N);
                N = "t8";
            }

            if (instruction.operands[2] instanceof IRConstantOperand){
                i_mipsInstructions.add("li $t9, " + val);
                val = "t9";
            }

            // load the address of the array to a temp register if it is a variable
            if (!registerMap.containsKey(instruction.operands[0])){
                i_mipsInstructions.add("la $t7, " + src);
                src = "t7";
            }

            // store N in the stack 
            if (instruction.operands[1] instanceof IRVariableOperand){
                int offset = varToStackMap.get(instruction.operands[1].toString());
                i_mipsInstructions.add("sw $" + N + ", " + offset + "($sp)");
            }

            // loop label 
            String slabel = currFunction + "_" + instruction.operands[0].toString() + "_loop"+instruction.operands[1] + ""+instruction.operands[2];
            i_mipsInstructions.add(slabel + ":");
            i_mipsInstructions.add("sw $" + val + ", ($" + src + ")");
            i_mipsInstructions.add("addi $" + src + ", $" + src + ", 4");
            i_mipsInstructions.add("addi $" + N + ", $" + N + ", -1");
            i_mipsInstructions.add("bne $" + N + ", $zero, " + slabel);
            // sw $t9, ($t7)
            // addi $t7, $t7, 4
            // addi $t8, $t8, -1
            // bne $t8, $zero, loop

            // load N back from the stack 
            if (instruction.operands[1] instanceof IRVariableOperand){
                int offset = varToStackMap.get(instruction.operands[1].toString());
                i_mipsInstructions.add("lw $" + N + ", " + offset + "($sp)");
            }
            
        } 
    }

    private void binaryOpsIm_h (IRInstruction instruction) {
        // op x, y, z -> x = y op z
        String dest = getRegForWriting(instruction.operands[0], "t2"); // either a variable or a parameter
        String src1 = getRegForReading(instruction.operands[1], "t3"); // either a variable, a parameter, or a constant
        String src2 = getRegForReading(instruction.operands[2], "t4"); // either a variable, a parameter, or a constant
        // both are constants
        if (instruction.operands[1] instanceof IRConstantOperand && instruction.operands[2] instanceof IRConstantOperand) {
            // use two immediate operands 
            switch (instruction.opCode) {
                case SUB:
                    // there is no subi, so we use load immediate to a temp register
                    i_mipsInstructions.add("li $t0, " + src1);
                    i_mipsInstructions.add("li $t1, " + src2);
                    i_mipsInstructions.add("sub $" + dest + ", $t0, $t1");
                    break;
                case ADD:
                    // x = x + y
                    i_mipsInstructions.add("addi $" + dest + ", $" + dest + ", " + src1);
                    // x = x + z
                    i_mipsInstructions.add("addi $" + dest + ", $" + dest + ", " + src2);
                    break;
                case AND:
                    // x = x & y
                    i_mipsInstructions.add("andi $" + dest + ", $" + dest + ", " + src1);
                    // x = x & z
                    i_mipsInstructions.add("andi $" + dest + ", $" + dest + ", " + src2);
                    break;
                case OR:
                    // x = x | y
                    i_mipsInstructions.add("ori $" + dest + ", $" + dest + ", " + src1);
                    // x = x | z
                    i_mipsInstructions.add("ori $" + dest + ", $" + dest + ", " + src2);
                    break;
                case MULT:
                    // no mult immediate, so we have to load the value to a temp registers
                    // x = y * z
                    i_mipsInstructions.add("li $t0, " + src1);
                    i_mipsInstructions.add("li $t1, " + src2);
                    i_mipsInstructions.add("mul $" + dest + ", $t0, $t1");
                    break;
                case DIV:
                    // x = y / z
                    i_mipsInstructions.add("li $t0, " + src1);
                    i_mipsInstructions.add("li $t1, " + src2);
                    i_mipsInstructions.add("div $" + dest +", $t0, $t1");
                    break;
                default:
                    break;
            }
            // save the value back to the stack if it is a variable 
            saveToStack(instruction.operands[0], dest);
            
        } else {
            // first one is a constant only
            if (instruction.operands[1] instanceof IRConstantOperand){
                switch (instruction.opCode) {
                    case SUB:
                        i_mipsInstructions.add("li $t0, " + src1);
                        i_mipsInstructions.add("sub $" + dest + ", $t0, $" + src2);
                        break;
                    case ADD:
                    i_mipsInstructions.add("addi $" + dest + ", $" + src2 + ", " + src1);
                        break;
                    case AND:
                    i_mipsInstructions.add("andi $" + dest + ", $" + src2 + ", " + src1);
                        break;
                    case OR:
                    i_mipsInstructions.add("ori $" + dest + ", $" + src2 + ", " + src1);
                        break;
                    case MULT:
                        // no mult immediate, so we have to load the value to a temp registers
                        // x = y * z
                        i_mipsInstructions.add("li $t0, " + src1);
                        i_mipsInstructions.add("mul $" + dest + ", $t0, $"+ src2);
                        break;
                    case DIV:
                        // x = y / z
                        i_mipsInstructions.add("li $t0, " + src1);
                        i_mipsInstructions.add("div $" + dest +", $t0, $"+ src2);
                        break;
                    default:
                        break;
                }
                // save the value back to the stack if it is a variable
                saveToStack(instruction.operands[0], dest);
            }
            else {
                // second one is a constant only
                switch (instruction.opCode) {
                    case SUB:
                    i_mipsInstructions.add("li $t0, " + src2);
                    i_mipsInstructions.add("sub $" + dest + ", $" + src1 + ", $t0");
                        break;
                    case ADD:
                    i_mipsInstructions.add("addi $" + dest + ", $" + src1 + ", " + src2);
                        break;
                    case AND:
                    i_mipsInstructions.add("andi $" + dest + ", $" + src1 + ", " + src2);
                        break;
                    case OR:
                    i_mipsInstructions.add("ori $" + dest + ", $" + src1 + ", " + src2);
                        break;
                    case MULT:
                        // no mult immediate, so we have to load the value to a temp registers
                        // x = y * z
                        i_mipsInstructions.add("li $t0, " + src2);
                        i_mipsInstructions.add("mul $" + dest + ", $t0, $"+ src1);
                        break;
                    case DIV:
                        // x = y / z
                        i_mipsInstructions.add("li $t0, " + src2);
                        i_mipsInstructions.add("div $" + dest +", $"+ src1 + ", $t0");
                        break;
                    default:
                        break;
                }
                // save the value back to the stack if it is a variable
                saveToStack(instruction.operands[0], dest);
            }
        }
    }


    private void binaryOpsVar_h (IRInstruction instruction) {
        // op x, y, z -> x = y op z
        String dest = getRegForWriting(instruction.operands[0], "t2"); // either a variable or a parameter
        String src1 = getRegForReading(instruction.operands[1], "t0"); // either a variable or a parameter
        String src2 = getRegForReading(instruction.operands[2], "t1"); // either a variable or a parameter

        String op = "";

        switch (instruction.opCode) {
            case ADD:
                op = "add";
                break;
            case SUB:
                op = "sub";
                break;
            case MULT:
                op = "mul";
                break;
            case AND:
                op = "and";
                break;
            case OR:
                op = "or";
                break;
            case DIV:
                op = "div";
                break;
            default:
                break;
        }
        
        String inst = op + " $" + dest + ", $" + src1 + ", $" + src2;
        i_mipsInstructions.add(inst);
        // save the value back to the stack if it is a variable
        saveToStack(instruction.operands[0], dest);
    }

    private void goto_h (IRInstruction instruction) {
        String inst = "j " + currFunction + "_" + getRegForReading(instruction.operands[0], "temp"); // a label
        i_mipsInstructions.add(inst);
    }

    private void branch_h (IRInstruction instruction) {
        String op = "";

        switch (instruction.opCode) {
            case BREQ:
                op = "beq";
                break;
            case BRNEQ:
                op = "bne";
                break;
            case BRLT:
                op = "blt";
                break;
            case BRGT:
                op = "bgt";
                break;
            case BRGEQ:
                op = "bge";
                break;
            //not actually a ble case, pretend like there is
            case BRLEQ:
                op = "ble";
                break;
            default:
                break;
        }
        String label = currFunction + "_" + getRegForReading(instruction.operands[0], "temp"); // a label
        String src1 = getRegForReading(instruction.operands[1], "t0");
        String src2 = getRegForReading(instruction.operands[2], "t1");
        if (instruction.operands[1] instanceof IRConstantOperand) {
            i_mipsInstructions.add("li $t0, " + src1);
            src1 = "t0";
        }

        if (instruction.operands[2] instanceof IRConstantOperand) {
            i_mipsInstructions.add("li $t1, " + src2);
            src2 = "t1";
        }
        String inst = op + " $" + src1 + ", $" + src2 + ", " + label;
        i_mipsInstructions.add(inst);
    }

    private void return_h (IRInstruction instruction) {
        // check if the return value is a variable or a constant
        String src = getRegForReading(instruction.operands[0], "t0"); // either a variable or a constant
        if (instruction.operands[0] instanceof IRVariableOperand) {
            i_mipsInstructions.add("move $v0, $" + src);
        } else {
            i_mipsInstructions.add("li $v0, " + src);
        }
        i_mipsInstructions.addAll(getEpilogue(currFunction));        
    }

    private void call_h (IRInstruction instruction) {
        //instrinsic IR functions: 
        //geti(), puti()

        switch (instruction.opCode){
            case CALL:
                String funcName = getRegForReading(instruction.operands[0], "t8"); // function name
                switch(funcName){
                    //In Tiger-IR, call, puti, t
                    // call, geti
                    //In MIPS, syscall, $v0 = 1, $a0 = integer to be printed
                    case ("puti"):
                        String t = getRegForReading(instruction.operands[1], "t0"); // to be printed
                        String inst = "move $a0, $" + t;
                        if (instruction.operands[1] instanceof IRConstantOperand){
                            inst = "li $a0, "+t;
                        }
                        i_mipsInstructions.add(inst);
                        i_mipsInstructions.add("li $v0, 1");
                        i_mipsInstructions.add("syscall");
                        break;
                    case ("geti"):
                        //In Tiger-IR, call, geti
                        i_mipsInstructions.add("li $v0, 5");
                        i_mipsInstructions.add("syscall");
                        break;
                    case ("getc"):
                        i_mipsInstructions.add("li $v0, 12");
                        i_mipsInstructions.add("syscall");
                        break;
                    case ("putc"):
                        t = getRegForReading(instruction.operands[1], "t0"); 
                        inst = "move $a0, $" + t;
                        if (instruction.operands[1] instanceof IRConstantOperand){
                            inst = "li $a0, "+t;
                        }
                        i_mipsInstructions.add(inst);
                        i_mipsInstructions.add("li $v0, 11");
                        i_mipsInstructions.add("syscall");
                        break;
                    //if it is custom function, do something else
                    default:
                    // save the variables and the old arguments to the stack
                    i_mipsInstructions.addAll(saveVarPar(currFunction));

                    for (int i = 1; i < instruction.operands.length; i++) {
                        String param = getRegForReading(instruction.operands[i], "t0");
                        if (instruction.operands[i] instanceof IRConstantOperand) {
                            // param is a constant
                            i_mipsInstructions.add("li $a" + (i-1) + ", " + param);
                        } else if (((IRVariableOperand) instruction.operands[i]).getType() instanceof IRArrayType && !registerMap.containsKey(instruction.operands[i])){
                            // param is an array in data segment
                            i_mipsInstructions.add("la $a" + (i-1) + ", " + param);
                        } else {
                            // either a variable or a parameter/array address as a param
                            i_mipsInstructions.add("move $a" + (i-1) + ", $" + param);
                        }
                    }
                    i_mipsInstructions.add("jal " + funcName);
                    // restore the arguments from the stack
                
                    i_mipsInstructions.addAll(restoreVarPar(currFunction));
                        break;
                }
                break;
            case CALLR:
                funcName = getRegForReading(instruction.operands[1], "t8"); // function name
                switch(funcName){
                    //In Tiger-IR, callr, a, geti
                    //In MIPS, syscall, $v0 = 5, $v0 is where integer is returned,
                    case ("geti"):
                        String dest = getRegForWriting(instruction.operands[0], "t0"); 
                        i_mipsInstructions.add("li $v0, 5");
                        i_mipsInstructions.add("syscall");
                        i_mipsInstructions.add("move $"+ dest + ", $v0");
                        // save the value back to the stack if it is a variable
                        saveToStack(instruction.operands[0], dest);
                        break;
                    //if it is custom function, do something else
                    default:
                    // save the variables and the old arguments to the stack
                    i_mipsInstructions.addAll(saveVarPar(currFunction));

                    for (int i = 2; i < instruction.operands.length; i++) {
                        String param = getRegForReading(instruction.operands[i], "t0");
                        if (instruction.operands[i] instanceof IRConstantOperand) {
                            // param is a constant
                            i_mipsInstructions.add("li $a" + (i-2) + ", " + param);
                        } else if (((IRVariableOperand) instruction.operands[i]).getType() instanceof IRArrayType){
                            // param is an array in data segment
                            i_mipsInstructions.add("la $a" + (i-2) + ", " + param);
                        } 
                        else {
                            // either a variable or a parameter/array address as a param
                            i_mipsInstructions.add("move $a" + (i-2) + ", $" + param);
                        }
                    }
                    i_mipsInstructions.add("jal " + funcName);
                    // restore the arguments from the stack
                    i_mipsInstructions.addAll(restoreVarPar(currFunction));

                    // load the return value to the variable
                    dest = getRegForWriting(instruction.operands[0], "t0");
                    i_mipsInstructions.add("move $" + dest + ", $v0");
                    // save the value back to the stack if it is a variable
                    saveToStack(instruction.operands[0], dest);
                    break;
                }
                break;
            default:
                break;
        }
    }

    

    private void array_h (IRInstruction instruction) {
        // array_load x, y, z -> x = y[z]
        // x: variable to store the value
        // y: array variable
        // z: index (int)

        // array_store x, y, z -> y[z] = x
        // x: value to store
        // y: array variable
        // z: index (int)
        switch (instruction.opCode) {
            case ARRAY_LOAD:
                // array_load x, y, z -> x = y[z]
                String dest = getRegForWriting(instruction.operands[0], "t2"); // where to store the value either a variable or a parameter
                String src = getRegForReading(instruction.operands[1], "t3"); // array variable - either an address or a parameter 
                String index = getRegForReading(instruction.operands[2], "t1"); // index - either a variable, a parameter, or a constant
                // if instruction.operands[1] in the map, then we already have the address 
                // else we have to load the address to a temp register
                if (!registerMap.containsKey(instruction.operands[1])){ 
                    // We need to load the address to a temp register
                    i_mipsInstructions.add("la $t0, " + src);
                // multiply the index by 4 to get the offset
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("lw $" + dest + ", " + index + "($t0)");
                    } else{
                        i_mipsInstructions.add("sll $t1, $" + index + ", 2");
                        i_mipsInstructions.add("add $t0, $t0, $t1");
                        i_mipsInstructions.add("lw $" + dest + ", ($t0)");
                    }
                } else {
                    // if it is a parameter, we already have the address loaded 
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("lw $" + dest + ", " + index + "($" + src + ")");
                    } else{
                        i_mipsInstructions.add("sll $t1, $" + index + ", 2");
                        i_mipsInstructions.add("add $t0, $" + src + ", $t1");
                        i_mipsInstructions.add("lw $" + dest + ", ($t0)");
                    }
                }
                // save the value back to the stack if it is a variable
                saveToStack(instruction.operands[0], dest);
                break;
            case ARRAY_STORE:
                // array_store x, y, z -> y[z] = x
                String value = getRegForReading(instruction.operands[0], "t2"); // value to store - either a variable, a parameter, or a constant
                src = getRegForReading(instruction.operands[1], "t3"); // array variable - either an address or a parameter
                index = getRegForReading(instruction.operands[2], "t1"); // index - either a variable, a parameter, or a constant
                // i = Integer.parseInt(index) * 4;
                // index = Integer.toString(i);
                if (!registerMap.containsKey(instruction.operands[1])){
                    // We need to load the address to a temp register
                    i_mipsInstructions.add("la $t0, " + src);
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("sw $" + value + ", " + index + "($t0)");
                    } else {
                        i_mipsInstructions.add("sll $t1, $" + index + ", 2");
                        i_mipsInstructions.add("add $t0, $t0, $t1");
                        i_mipsInstructions.add("sw $" + value + ", ($t0)");
                    }
                } else {
                    // if it is a parameter, we already have the address loaded
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("sw $" + value + ", " + index + "($" + src + ")");
                    } else {
                        i_mipsInstructions.add("sll $t1, $" + index + ", 2");
                        i_mipsInstructions.add("add $t0, $" + src + ", $t1");
                        i_mipsInstructions.add("sw $" + value + ", ($t0)");
                    }
                    // mipsInstructions.add("sw $" + value + ", " + index + "($" + src + ")");
                }
                break;
            default:
                break;
        }   
    }
}