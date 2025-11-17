package ir;

import ir.operand.*;
import java.util.*;
import ir.datatype.*;

// ADD, ADDI, SUB, MUL, DIV, AND, ANDI, OR, ORI, SLL, // bin ops
// LI, LW, MOVE, SW, LA, // data movement
// BEQ, BNE, BLT, BGT, BGE, // branches
// J, JAL, JR, // jumps


public class InstructionSelectorGreedy {
    private List < String > g_mipsInstructions; // final MIPS instructions
    private List < String > i_mipsInstructions; // intermediate MIPS instructions
    private List < String > dataSegment; // data segment
    private IRProgram program; // IR program
    private IRFunction currFunction; // current function
    private Map < IRVariableOperand, String > registerMap; // map parameters to registers
    private Map < IRVariableOperand, String > blockRegMap; // map variables to registers (for each block)
    private Map<String, Integer> varToStackMap; // map variables to stack offset
    private CFG currCfg; // current CFG
    private List<BasicBlock> currBlocks; // current blocks
    private BasicBlock currB; // current block
    private Map<IRVariableOperand, Integer> varCount; // count the number of occurrences of each variable in the block

    public InstructionSelectorGreedy(IRProgram pr) {
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
                    // put the array in the data segment with the size
                    dataSegment.add(function+var.toString() + ": .space " + ((IRArrayType) var.getType()).getSize());
                } else {
                    // dataSegment.add(function+"_"+var.toString() + ": .word 0");
                }
            }
        }

        g_mipsInstructions.addAll(dataSegment);
        g_mipsInstructions.add(".text");

        for (IRFunction function : program.functions) {
            // if it is the main function, select instructions for it first
            if (function.name.equals("main")){
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
                System.err.println("Function: " + function.name);
                System.err.println(g_mipsInstructions.get(2));
            }
        }

        for (IRFunction function : program.functions) {
            // select for all other instructions except main after selecting for main
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
                System.err.println("Function: " + function.name);
                System.err.println(g_mipsInstructions.get(2));
            }
        }

        return g_mipsInstructions;
    }

    private List<String> selectInstructionsForFunction(IRFunction function) {
        List<String> mipsI = new ArrayList<>();
        mipsI.add(function.name + ":");

        mipsI.addAll(getPrologue(function));

        // initialize all variables to 0
        mipsI.add("li $t0, 0");
        for (IRVariableOperand var: function.getVarOnly()) {
            // store the variables in the stack - except arrays
            if (!(var.getType() instanceof IRArrayType)) {
                int loc = varToStackMap.size()*4 + 8;
                varToStackMap.put(var.toString(), loc);
                // assign 0 to all variables
                mipsI.add("# initializing: " + var.toString());
                mipsI.add("sw $t0, " + loc + "($sp)");
            }
        }

        currCfg = new CFG(function);
        currBlocks = currCfg.basicBlocks;

        for (BasicBlock block : currBlocks) {
            currB = block;

            // intra-block analysis
            getUEVAR(block); 
            countOcc(block);
            regAlloc(block);
            
            IRInstruction fiInstruction = block.getInstructions().get(0);
            IRInstruction laInstruction = null;
            
            // load all values that has an assigned register in the block
            // if it is not a label load before the instruction, if it is a label load after it
            if (!isALabel(fiInstruction)){
                mipsI.add("# Block Loading: ");
                mipsI.addAll(loadBlock(block));
            }

            mipsI.add("# Block Running: ");
            for (IRInstruction instruction : block.getInstructions()) {
                laInstruction = instruction;

                // save all values that has an assigned register in the block, before a jump
                if (isAjump(instruction)){
                    mipsI.add("# Block Saving - before jump: ");
                    mipsI.addAll(storeBlock(block));
                }

                selectInstructionsForInstruction(instruction);
                mipsI.addAll(i_mipsInstructions);

                // save all values that has an assigned register in the block, after a label
                if (isALabel(instruction)){
                    mipsI.add("# Block Loading: - after label: ");
                    mipsI.addAll(loadBlock(block));
                }
            }

            // if the last instruction is not a jump, save all values that has an assigned register in the block
            // if it is a jump, we will save the values before the jump
            if (!isAjump(laInstruction)){
            mipsI.add("# Block Saving: ");
                mipsI.addAll(storeBlock(block));
            }

        }

        // epilogue -- restore values and then return 

        mipsI.addAll(getEpilogue(function));
        
        return mipsI;
    }

    // save all values that has an assigned register in the block back to the stack 
    private List<String> storeBlock (BasicBlock bb){
        List<String> storeBlock = new ArrayList<>();
        for (Map.Entry<IRVariableOperand, String> entry : blockRegMap.entrySet()) {
            int offset = varToStackMap.get(entry.getKey().toString());
            storeBlock.add("sw $" + entry.getValue() + ", " + offset + "($sp)");
        }
        return storeBlock;
    }

    // get the set of variables that are used before they are defined in the block
    private void getUEVAR(BasicBlock block) {
        Set<IRVariableOperand> ueVar = new HashSet<>();
        Set<IRVariableOperand> defSoFar = new HashSet<>();
        for (IRInstruction instruction : block.getInstructions()) {
            IROperand [] sources = instruction.getSources();
            if (sources != null) {
                for (IROperand source : sources) {
                    if (source instanceof IRVariableOperand && !defSoFar.contains(source)) {
                        ueVar.add((IRVariableOperand) source);
                    }
                }
            }

            IROperand target = instruction.getTarget();
            if (target instanceof IRVariableOperand) {
                defSoFar.add((IRVariableOperand) target);
            }
        }

        // dump block 
        System.err.println("Block UEVAR: " + block.getInstructions().get(0).toString() + " to " + block.getInstructions().get(block.getInstructions().size()-1).toString());
        for (IRVariableOperand var : ueVar) {
            System.err.println("Var: " + var.toString());
        }
        block.uevar = ueVar;
    }

    // load all values that has an assigned register in the block if needed 
    private List<String> loadBlock (BasicBlock bb){
        Set<IRVariableOperand> ueVar = bb.uevar;
        List<String> loadBlock = new ArrayList<>();
        for (Map.Entry<IRVariableOperand, String> entry : blockRegMap.entrySet()) {
            if (ueVar.contains(entry.getKey())) {
                int offset = varToStackMap.get(entry.getKey().toString());
                loadBlock.add("lw $" + entry.getValue() + ", " + offset + "($sp)");
            }
        }
        return loadBlock;
    }

    // the following two functions returns the register to be used for writing/reading the value of the variable
    // if the variable is a parameter, it returns the 'a' register
    // if the variable is assigned to a register in the block, it returns the register assigned 
    // if the variable is in the stack, it loads the value from the stack to a temp register and returns the temp register
    // if it was an array in the data segment, it returns the label of the array
    // if it is a constant or a label, it returns the value as is 


    // we don't load the value as we are overwriting it
    private String getRegForWriting(IROperand var, String tempReg) {
        // System.err.println("Function: " + currFunction.name);
        // System.err.println("--- Var: " + var.toString());
        if (var instanceof IRVariableOperand) {
            // if it is a parameter, return the 'a' register
            if (registerMap.containsKey(var)) {
                // System.err.println(" --- --- Var in map");
                return registerMap.get(var);
            }
            // if it is assigned to a register in the block, return the register
            if (blockRegMap.containsKey(var)) {
                return blockRegMap.get(var);
            }
            // if it is in the stack, load the value from the stack to a temp register and return the temp register
            if (varToStackMap.containsKey(var.toString())) {
                // we are writing back to the stack here, so we don't need to load the value
                return tempReg;
            }
        }

        // if it is an array, then return it is name in the data segment
        if (currFunction.variables.contains(var)) {
            return currFunction+var.toString();
        }

        // if it is a constant, function name, or label, return it as is 
        return var.toString();
    }

    // we load the value as we are reading it
    private String getRegForReading(IROperand var, String tempReg) {
        // System.err.println("Function: " + currFunction.name);
        // System.err.println("--- Var: " + var.toString());
        if (var instanceof IRVariableOperand) {
            // if it is a parameter, return the 'a' register
            if (registerMap.containsKey(var)) {
                // System.err.println(" --- --- Var in map");
                return registerMap.get(var);
            }

            // if it is assigned to a register in the block, return the register
            if (blockRegMap.containsKey(var)) {
                return blockRegMap.get(var);
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

    // save the value back to the stack if it is a variable and not assigned to a register in the block
    private void saveToStack(IROperand var, String reg) {
        // if it is a variable, and not assigned to a register in the block, save it back to the stack
        if (var instanceof IRVariableOperand) {
            // Save it back to the stack only if it is a variable -- not a parameter
            if (varToStackMap.containsKey(var.toString()) && !blockRegMap.containsKey(var)) {
                i_mipsInstructions.add("sw $" + reg + ", " + varToStackMap.get(var.toString()) + "($sp)");
            }
        }
    }

    // count the number of occurrences of each variable in the block
    private void countOcc(BasicBlock block){
        // count the number of occurrences of each variable (non parameters) in the block
        varCount = new HashMap<>();
        for (IRInstruction instruction : block.getInstructions()) {
            IROperand [] operands = instruction.operands;
            for (IROperand op : operands){
                if (op instanceof IRVariableOperand && !currFunction.parameters.contains(op) && !(( (IRVariableOperand) op).getType() instanceof IRArrayType)){
                    if (varCount.containsKey(op)){
                        varCount.put((IRVariableOperand) op, varCount.get(op) + 1);
                    } else {
                        varCount.put((IRVariableOperand) op, 1);
                    }
                }
            }

        }

        // dump map
        System.err.println("Var Count: ");
        for (Map.Entry<IRVariableOperand, Integer> entry : varCount.entrySet()) {
            System.err.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
        }

    }

    // allocate registers to the most frequent vars
    private void regAlloc (BasicBlock block){
        // go over the vars in the varCount map and allocate registers to the most frequent ones from the temp registers
        // if there are more vars than the number of temp registers, we will have to spill some of them to the stack

        // sort the map by the number of occurrences
        List<Map.Entry<IRVariableOperand, Integer>> list = new LinkedList<>(varCount.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<IRVariableOperand, Integer>>() {
            public int compare(Map.Entry<IRVariableOperand, Integer> o1, Map.Entry<IRVariableOperand, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        blockRegMap = new HashMap<>(); // reset the blockRegMap

        // allocate registers to the most frequent vars
        int i = 0;
        for (Map.Entry<IRVariableOperand, Integer> entry : list) {
            if (i < 7) {
                blockRegMap.put(entry.getKey(), "t" + i);
                i++;
            } 
        }
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
        // epilogue.add("lw $fp, 4($sp)");
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

        saveVarPar.addAll(storeBlock(currB));

        // save the parameters to the stack
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

        // restore the variables from the stack (if they are in the map)
        restoreVarPar.addAll(loadBlock(currB));
        
        // restore the parameters from the stack
        int n = varToStackMap.size();
        for (int i = 0; i < function.parameters.size(); i++) {
            int offset = (n * 4) + 8 + (i * 4);
            String inst = "lw $a" + i + ", " + offset + "($sp)";
            restoreVarPar.add(inst);
        }
        
        return restoreVarPar;
    }

    private List<String> restoreVarParSys(IRFunction function) {
        List<String> restoreVarPar = new ArrayList<>();
        // the first 8 bytes are for the return address and the frame pointer -- already restored in the epilogue
        // the next n * 4 bytes are for the variables -- will be restored whenever they are used
        // the next m * 4 bytes are for the parameters -- restore them from the stack

        for (Map.Entry<IRVariableOperand, String> entry : blockRegMap.entrySet()) {
            String dest = entry.getValue();
            String src = dest.replace('t', 's');
            restoreVarPar.add("move $" + dest + ", $" + src);
        }
        
        // restore the parameters from the stack
        int n = varToStackMap.size();
        for (int i = 0; i < function.parameters.size(); i++) {
            int offset = (n * 4) + 8 + (i * 4);
            String inst = "lw $a" + i + ", " + offset + "($sp)";
            restoreVarPar.add(inst);
        }
        
        return restoreVarPar;
    }

    private List<String> saveVarParSys(IRFunction function) {
        List<String> saveVarPar = new ArrayList<>();
        // the first 8 bytes are for the return address and the frame pointer -- already saved in the prologue
        // the next n * 4 bytes are for the variables -- already saved whenever they are assigned
        // the next m * 4 bytes are for the parameters -- save them to the stack

        // move all variables with assigned reg to $s
        for (Map.Entry<IRVariableOperand, String> entry : blockRegMap.entrySet()) {
            String src = entry.getValue();
            String dest = src.replace('t', 's');
            saveVarPar.add("move $" + dest + ", $" + src);
        }


        // save the parameters to the stack
        int n = varToStackMap.size();
        for (int i = 0; i < function.parameters.size(); i++) {
            int offset = (n * 4) + 8 + (i * 4);
            String inst = "sw $a" + i + ", " + offset + "($sp)";
            saveVarPar.add(inst);
        }
        return saveVarPar;
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

    private boolean isAjump(IRInstruction instruction) {
        switch (instruction.opCode) {
            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRGEQ:
            case BRLEQ:
            case GOTO:
                return true;
            default:
                return false;
        }
    }

    private boolean isALabel(IRInstruction instruction) {
        switch (instruction.opCode) {
            case LABEL:
                return true;
            default:
                return false;
        }
    }

    private void assign_h (IRInstruction instruction) {
        // normal assignment
        if (instruction.operands.length == 2){
        String dest = getRegForWriting(instruction.operands[0], "t7"); // either a variable or a parameter 
        String src = getRegForReading(instruction.operands[1], "t8"); // either a variable, a parameter, or a constant
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
        String dest = getRegForWriting(instruction.operands[0], "t7"); // either a variable or a parameter
        String src1 = getRegForReading(instruction.operands[1], "t8"); // either a variable, a parameter, or a constant
        String src2 = getRegForReading(instruction.operands[2], "t9"); // either a variable, a parameter, or a constant
        // both are constants
        if (instruction.operands[1] instanceof IRConstantOperand && instruction.operands[2] instanceof IRConstantOperand) {
            // use two immediate operands 
            switch (instruction.opCode) {
                case SUB:
                    // there is no subi, so we use load immediate to a temp register
                    i_mipsInstructions.add("li $t8, " + src1);
                    i_mipsInstructions.add("li $t9, " + src2);
                    i_mipsInstructions.add("sub $" + dest + ", $t8, $t9");
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
                    i_mipsInstructions.add("li $t8, " + src1);
                    i_mipsInstructions.add("li $t9, " + src2);
                    i_mipsInstructions.add("mul $" + dest + ", $t8, $t9");
                    break;
                case DIV:
                    // x = y / z
                    i_mipsInstructions.add("li $t8, " + src1);
                    i_mipsInstructions.add("li $t9, " + src2);
                    i_mipsInstructions.add("div $" + dest +", $t8, $t9");
                    break;
                default:
                    break;
            }
            // save the value back to the stack if it is a variable and not in the map
            saveToStack(instruction.operands[0], dest);
            
        } else {
            // first one is a constant only
            if (instruction.operands[1] instanceof IRConstantOperand){
                switch (instruction.opCode) {
                    case SUB:
                        i_mipsInstructions.add("li $t8, " + src1);
                        i_mipsInstructions.add("sub $" + dest + ", $t8, $" + src2);
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
                        i_mipsInstructions.add("li $t8, " + src1);
                        i_mipsInstructions.add("mul $" + dest + ", $t8, $"+ src2);
                        break;
                    case DIV:
                        // x = y / z
                        i_mipsInstructions.add("li $t8, " + src1);
                        i_mipsInstructions.add("div $" + dest +", $t8, $"+ src2);
                        break;
                    default:
                        break;
                }
                // save the value back to the stack if it is a variable
                saveToStack(instruction.operands[0], dest);
            }
            else {
                // the second op is a constant only
                switch (instruction.opCode) {
                    case SUB:
                    i_mipsInstructions.add("li $t9, " + src2);
                    i_mipsInstructions.add("sub $" + dest + ", $" + src1 + ", $t9");
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
                        i_mipsInstructions.add("li $t9, " + src2);
                        i_mipsInstructions.add("mul $" + dest + ", $t9, $"+ src1);
                        break;
                    case DIV:
                        // x = y / z
                        i_mipsInstructions.add("li $t9, " + src2);
                        i_mipsInstructions.add("div $" + dest +", $"+ src1 + ", $t9");
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
        String dest = getRegForWriting(instruction.operands[0], "t7"); // either a variable or a parameter
        String src1 = getRegForReading(instruction.operands[1], "t8"); // either a variable or a parameter
        String src2 = getRegForReading(instruction.operands[2], "t9"); // either a variable or a parameter

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
        String src1 = getRegForReading(instruction.operands[1], "t7");
        String src2 = getRegForReading(instruction.operands[2], "t8");
        if (instruction.operands[1] instanceof IRConstantOperand) {
            i_mipsInstructions.add("li $t7, " + src1);
            src1 = "t7";
        }

        if (instruction.operands[2] instanceof IRConstantOperand) {
            i_mipsInstructions.add("li $t8, " + src2);
            src2 = "t8";
        }
        String inst = op + " $" + src1 + ", $" + src2 + ", " + label;
        i_mipsInstructions.add(inst);
    }

    private void return_h (IRInstruction instruction) {
        // check if the return value is a variable or a constant
        String src = getRegForReading(instruction.operands[0], "t7"); // either a variable or a constant
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
                String funcName = getRegForReading(instruction.operands[0], "temp"); // function name
                switch(funcName){
                    //In Tiger-IR, call, puti, t
                    // call, geti
                    //In MIPS, syscall, $v0 = 1, $a0 = integer to be printed
                    case ("puti"):
                        i_mipsInstructions.addAll(saveVarParSys(currFunction));
                        
                        String t = getRegForReading(instruction.operands[1], "t7"); // to be printed
                        String inst = "move $a0, $" + t;
                        if (instruction.operands[1] instanceof IRConstantOperand){
                            inst = "li $a0, "+t;
                        }
                        i_mipsInstructions.add(inst);
                        i_mipsInstructions.add("li $v0, 1");
                        i_mipsInstructions.add("syscall");
                        
                        i_mipsInstructions.addAll(restoreVarParSys(currFunction));
                        break;
                    case ("geti"):
                        //In Tiger-IR, call, geti
                        i_mipsInstructions.addAll(saveVarParSys(currFunction));

                        i_mipsInstructions.add("li $v0, 5");
                        i_mipsInstructions.add("syscall");

                        i_mipsInstructions.addAll(restoreVarParSys(currFunction));
                        break;
                    case ("getc"):
                        i_mipsInstructions.addAll(saveVarParSys(currFunction));
                        
                        i_mipsInstructions.add("li $v0, 12");
                        i_mipsInstructions.add("syscall");

                        i_mipsInstructions.addAll(restoreVarParSys(currFunction));
                        break;
                    case ("putc"):
                        i_mipsInstructions.addAll(saveVarParSys(currFunction));
                        
                        t = getRegForReading(instruction.operands[1], "t7"); // to be printed
                        inst = "move $a0, $" + t;
                        if (instruction.operands[1] instanceof IRConstantOperand){
                            inst = "li $a0, "+t;
                        }
                        i_mipsInstructions.add(inst);
                        i_mipsInstructions.add("li $v0, 11");
                        i_mipsInstructions.add("syscall");
                        
                        i_mipsInstructions.addAll(restoreVarParSys(currFunction));
                        break;
                    //if it is custom function, do something else
                    default:
                    // save the variables and the old arguments to the stack before a function call
                    i_mipsInstructions.addAll(saveVarPar(currFunction));

                    for (int i = 1; i < instruction.operands.length; i++) {
                        String param = getRegForReading(instruction.operands[i], "t7");
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
                    
                    // restore the var and args from the stack 
                    i_mipsInstructions.addAll(restoreVarPar(currFunction));
                        break;
                }
                break;
            case CALLR:
                funcName = getRegForReading(instruction.operands[1], "temp"); // function name
                switch(funcName){
                    //In Tiger-IR, callr, a, geti
                    //In MIPS, syscall, $v0 = 5, $v0 is where integer is returned,
                    case ("geti"):
                        i_mipsInstructions.addAll(saveVarParSys(currFunction));
                        
                        String dest = getRegForWriting(instruction.operands[0], "t7"); 
                        i_mipsInstructions.add("li $v0, 5");
                        i_mipsInstructions.add("syscall");

                        i_mipsInstructions.addAll(restoreVarParSys(currFunction));
                        
                        i_mipsInstructions.add("move $"+ dest + ", $v0");
                        // save the value back to the stack if it is a variable
                        saveToStack(instruction.operands[0], dest);
                        break;
                    //if it is custom function, do something else
                    default:
                    // save the variables and the old arguments to the stack
                    i_mipsInstructions.addAll(saveVarPar(currFunction));

                    for (int i = 2; i < instruction.operands.length; i++) {
                        String param = getRegForReading(instruction.operands[i], "t7");
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
                    dest = getRegForWriting(instruction.operands[0], "t7");
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
                String dest = getRegForWriting(instruction.operands[0], "t7"); // where to store the value either a variable or a parameter
                String src = getRegForReading(instruction.operands[1], "t8"); // array variable - either an address or a parameter 
                String index = getRegForReading(instruction.operands[2], "t9"); // index - either a variable, a parameter, or a constant
                // if instruction.operands[1] in the map, then we already have the address 
                // else we have to load the address to a temp register
                if (!registerMap.containsKey(instruction.operands[1])){ 
                    // We need to load the address to a temp register
                    i_mipsInstructions.add("la $t8, " + src);
                // multiply the index by 4 to get the offset
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("lw $" + dest + ", " + index + "($t0)");
                    } else{
                        i_mipsInstructions.add("sll $t9, $" + index + ", 2");
                        i_mipsInstructions.add("add $t8, $t8, $t9");
                        i_mipsInstructions.add("lw $" + dest + ", ($t8)");
                    }
                } else {
                    // if it is a parameter, we already have the address loaded 
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("lw $" + dest + ", " + index + "($" + src + ")");
                    } else{
                        i_mipsInstructions.add("sll $t9, $" + index + ", 2");
                        i_mipsInstructions.add("add $t8, $" + src + ", $t9");
                        i_mipsInstructions.add("lw $" + dest + ", ($t8)");
                    }
                }
                // save the value back to the stack if it is a variable
                saveToStack(instruction.operands[0], dest);
                break;
            case ARRAY_STORE:
                // array_store x, y, z -> y[z] = x
                String value = getRegForReading(instruction.operands[0], "t7"); // value to store - either a variable, a parameter, or a constant
                src = getRegForReading(instruction.operands[1], "t8"); // array variable - either an address or a parameter
                index = getRegForReading(instruction.operands[2], "t9"); // index - either a variable, a parameter, or a constant
                // i = Integer.parseInt(index) * 4;
                // index = Integer.toString(i);
                if (!registerMap.containsKey(instruction.operands[1])){
                    // We need to load the address to a temp register
                    i_mipsInstructions.add("la $t8, " + src);
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("sw $" + value + ", " + index + "($t8)");
                    } else {
                        i_mipsInstructions.add("sll $t9, $" + index + ", 2");
                        i_mipsInstructions.add("add $t8, $t8, $t9");
                        i_mipsInstructions.add("sw $" + value + ", ($t8)");
                    }
                } else {
                    // if it is a parameter, we already have the address loaded
                    if (instruction.operands[2] instanceof IRConstantOperand){
                        int i = Integer.parseInt(index) * 4;
                        index = Integer.toString(i);
                        i_mipsInstructions.add("sw $" + value + ", " + index + "($" + src + ")");
                    } else {
                        i_mipsInstructions.add("sll $t9, $" + index + ", 2");
                        i_mipsInstructions.add("add $t8, $" + src + ", $t9");
                        i_mipsInstructions.add("sw $" + value + ", ($t8)");
                    }
                }
                break;
            default:
                break;
        }   
    }
}