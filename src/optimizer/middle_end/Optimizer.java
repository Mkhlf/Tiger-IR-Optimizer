package middle_end;

import ir.*;
import ir.IRInstruction.OpCode;
import ir.datatype.*;
import ir.operand.*;

import java.util.*;

// Optimizer class
class Optimizer {
    private IRProgram program;
    private Set<IRInstruction> markedInstructions;
    private Map<IRFunction, CFG> cfgs;
    private Map<CFG, Map<BasicBlock, Set<IRInstruction>>> INs;
    private Map<CFG, Map<BasicBlock, Set<IRInstruction>>> OUTs;

    Optimizer(IRProgram program) {
        this.program = program;
        this.markedInstructions = new HashSet<>();
        this.INs = new HashMap<>();
        this.OUTs = new HashMap<>();
        this.cfgs = new HashMap<>();
    }

    void optimize() {
        // create cfg for each function
        // compute reaching definitions within each function cfg 
        // mark dead code in each function
        // eliminate dead code in each function // all app 
        // markDeadCode_linear();
        // eliminateDeadCode();
        
        for (IRFunction function : program.functions) {
            CFG cfg = new CFG(function);
            cfgs.put(function, cfg);
            computeReachingDefinitions(cfg);
            markDeadCode_cfg(function);
            eliminateDeadCode(function);
        }
        // dump_in_out();
    }

    private void computeReachingDefinitions (CFG cfg){
        // initialize the reaching definitions for each block
        IRFunction function = cfg.function;
        Map <BasicBlock, Set<IRInstruction>> gen = new HashMap<>();  // set of definitions(instructions) generated in the block
        Map <BasicBlock, Set<IRInstruction>> kill = new HashMap<>(); // set of definitions that are might be killed by other blocks
        
        // compute gen and kill sets for each basic block

        for (BasicBlock bb : cfg.basicBlocks){
            Set <IRInstruction> newGen = new HashSet<>();
            Set <IRInstruction> newKill = new HashSet<>();
            for (IRInstruction inst : bb.getInstructions()){
                switch (inst.opCode){
                    case ASSIGN:
                    case ADD:
                    case SUB:
                    case MULT:
                    case DIV:
                    case AND:
                    case OR:
                    case CALLR:
                    case ARRAY_LOAD:
                    case ARRAY_STORE:
                    // a gen instruction 
                        newGen.add(inst);
                        break;
                    default:
                        break;
                }
            }
            // go over the instructions in the genSet, and go over all the instruction in the code 
            // go over the rest of the instructions, and see what could kill any of these instrcutions 
            for (IRInstruction def : newGen){
                IROperand defVar = def.operands[0]; // left hand side of the definition
                IROperand offSet = null; // offset for array store (index)
                if (def.opCode == OpCode.ARRAY_STORE){
                    defVar = def.operands[1];
                    offSet = def.operands[2];
                }
                for (IRInstruction currInstruction : function.getInstructions()){
                    if (bb.getInstructions().contains(currInstruction)){
                        // skip the instructions if in the same block
                        continue;
                    }
                    IROperand currVar;
                    switch (currInstruction.opCode){
                        case ASSIGN:
                        case ADD:
                        case SUB:
                        case MULT:
                        case DIV:
                        case AND:
                        case OR:
                        case CALLR:
                        case ARRAY_LOAD:
                            currVar = currInstruction.operands[0];
                            if (currVar.equals(defVar)){
                                newKill.add(currInstruction);
                            }
                            break;

                        case ARRAY_STORE:
                            currVar = currInstruction.operands[1];
                            IROperand currOffset = currInstruction.operands[2];
                            if (currVar.equals(defVar) && (currOffset.equals(offSet) || offSet == null)){
                                newKill.add(currInstruction);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
            gen.put(bb, newGen);
            kill.put(bb, newKill);
        }

        // initialize the in and out sets for each block
        Map <BasicBlock, Set<IRInstruction>> in = new HashMap<>();
        Map <BasicBlock, Set<IRInstruction>> out = new HashMap<>();
        for (BasicBlock bb : cfg.basicBlocks){
            in.put(bb, new HashSet<>()); // empty set
            out.put(bb, gen.get(bb)); // gen[bb]
        }

        // iterate over the blocks and compute the in and out sets until they converge
        
        // out = gen U (in - kill)
        System.err.println("=============Computing IN and OUT sets============");
        boolean changed = true;
        while (changed){
            changed = false;
            for (BasicBlock bb : cfg.basicBlocks){
                System.err.println("BB: " + bb.getStartLine());
                // in = union of all predecessors outs
                Set <IRInstruction> newIn = new HashSet<>();
                System.err.println("");
                System.err.print("      Predecessors:");
                for (BasicBlock pred : bb.getPredecessors()){
                    System.err.print(pred.getStartLine()+ ", ");
                    newIn.addAll(out.get(pred));
                }
                System.err.println("");
                System.err.println("    New In: ");
                for (IRInstruction instr : newIn){
                    System.err.print(instr.irLineNumber + ", ");
                }
                System.err.println("");
                // System.err.println("=============");
                in.put(bb, newIn);
                // if (!newIn.equals(in.get(bb))){
                //     changed = true;
                //     in.put(bb, newIn); // update the in set
                // }
                
                // out = gen U (in - kill)
                Set <IRInstruction> newOut = new HashSet<>();
                newOut.addAll(gen.get(bb));
                for (IRInstruction inst : in.get(bb)){
                    if (!kill.get(bb).contains(inst)){
                        newOut.add(inst);
                    }
                }
                if (!newOut.equals(out.get(bb))){
                    changed = true;
                    out.put(bb, newOut);
                }
            }
        }
        // store the in and out sets
        INs.put(cfg, in);
        OUTs.put(cfg, out);
    }

    private void markDeadCode_cfg(IRFunction function) {
        // find all critical instructions in the function and mark them
        // 
        markedInstructions.clear();
        List <IRInstruction> workList = new ArrayList<>();
        CFG cfg = cfgs.get(function);
        
        //get list of critical inst
        for (IRInstruction instruction: function.getInstructions()) {          // over all BB and get the instructions from 
            if (isCrit(instruction)) {
                markedInstructions.add(instruction);
                workList.add(instruction);
            }
        }

        // while worklist is not empty
        // get an instruction from the worklist
        // get the block of the instruction
        // loop within the block to find the nearest def. that is alive before the critical instruction, 
            // if found then mark it and add it to the worklist and move to the next critical instruction

        // otherwise look into the IN[currentBlock] and mark all the instruction that write to this one. 
        // end while
        System.err.println("Start of marking - " + function.name);
        while (!workList.isEmpty()){
            IRInstruction critInst = workList.remove(0);
            BasicBlock critBlock = cfg.instrToBlock.get(critInst);
            IROperand[] critOp = critInst.operands;
            IRInstruction writingInst = null;
            Set <IRInstruction> inSet = INs.get(cfg).get(critBlock);
            switch (critInst.opCode){
                case GOTO:
                case LABEL:
                    break;
                // check the 2nd onwards 
                case ARRAY_LOAD:
                case ASSIGN:
                case CALL:
                case BREQ:
                case BRNEQ:
                case BRLT:
                case BRGT:
                case BRLEQ:
                case BRGEQ:
                case ADD:
                case SUB:
                case MULT:
                case DIV:
                case AND:
                case OR:
                    for (int i = 1; i < critOp.length; i++) {
                        writingInst = null;
                        // 1. check within the same block upto this instruction
                        for (IRInstruction currInstruction : critBlock.getInstructions()){
                            if (currInstruction.equals(critInst)){
                                break;
                            }
                            if (isWriting(currInstruction, critOp[i])){
                                writingInst = currInstruction;
                            }
                        }
                        if (writingInst != null){
                            if (!markedInstructions.contains(writingInst)){
                                markedInstructions.add(writingInst);
                                workList.add(writingInst);
                                System.err.println(critInst + " Marking instruction - BB: " + writingInst);
                            } else {
                                System.err.println(critInst + " Found instruction - BB: " + writingInst);
                            }
                            continue;
                        }
                        // 2. check within the IN set of the block
                        for (IRInstruction instr : inSet){
                            
                            if (markedInstructions.contains(instr)){
                                continue;
                            }
                            
                            if (isWriting(instr, critOp[i])){
                                markedInstructions.add(instr);
                                workList.add(instr);
                                System.err.println(critInst + " Marking instruction - IN: " + instr);
                            }
                        }
                    }
                    break;
                        
                case RETURN:
                    // check the first op 
                    System.err.println("    1st only");
                    writingInst = null;
                    // 1. check within the same block upto this instruction
                    for (IRInstruction currInstruction : critBlock.getInstructions()){
                        if (currInstruction.equals(critInst)){
                            break;
                        }
                        if (isWriting(currInstruction, critOp[0])){
                            writingInst = currInstruction;
                        }
                    }
                    if (writingInst != null){
                        if (!markedInstructions.contains(writingInst)){
                            markedInstructions.add(writingInst);
                            workList.add(writingInst);
                            System.err.println(critInst + " Marking instruction - BB: " + writingInst);
                        } else {
                            System.err.println(critInst + " Found instruction - BB: " + writingInst);
                        }
                    }
                    // 2. check within the IN set of the block
                    for (IRInstruction instr : inSet){
                        if (markedInstructions.contains(instr)){
                            continue;
                        }
                        if (isWriting(instr, critOp[0])){
                            markedInstructions.add(instr);
                            workList.add(instr);
                            System.err.println(critInst + " Marking instruction - IN: " + instr);
                        }
                    }
                    break;

                case CALLR:
                    // System.err.println("Checking Array Load ");
                    // check the 3rd op onwards
                    System.err.println("    3rd onwards");
                    for (int i = 2; i < critOp.length; i++) {
                        writingInst = null;
                    // 1. check within the same block upto this instruction
                        for (IRInstruction currInstruction : critBlock.getInstructions()){
                            if (currInstruction.equals(critInst)){
                                break;
                            }
                            if (isWriting(currInstruction, critOp[i])){
                                writingInst = currInstruction;
                            }
                        }
                        if (writingInst != null){
                            if (!markedInstructions.contains(writingInst)){
                                markedInstructions.add(writingInst);
                                workList.add(writingInst);
                                System.err.println(critInst + " Marking instruction - BB: " + writingInst);
                            } else {
                                System.err.println(critInst + " Found instruction - BB: " + writingInst);
                            }
                        }
                    // 2. check within the IN set of the block
                        for (IRInstruction instr : inSet){
                            if (markedInstructions.contains(instr)){
                                continue;
                            }
                            if (isWriting(instr, critOp[i])){
                                markedInstructions.add(instr);
                                workList.add(instr);
                                System.err.println(critInst + " Marking instruction - IN: " + instr);
                            }
                        }
                    }
                    break;
                case ARRAY_STORE:
                    // System.err.println();
                    // System.err.println("Checking array store with oprands: " + critInst.operands[0].toString() + ", " + critInst.operands[1].toString() + " " + critInst.operands[2].toString());
                    // System.err.println();
                    // check the 1st and 3rd op
                    for (int i = 0; i ==0  || i == 2; i+=2) {
                        System.err.println(" oprand: " + critOp[i]);
                        writingInst = null;
                    // 1. check within the same block upto this instruction
                        for (IRInstruction currInstruction : critBlock.getInstructions()){
                            if (currInstruction.equals(critInst)){
                                break;
                            }
                            if (isWriting(currInstruction, critOp[i])){
                                writingInst = currInstruction;
                            }
                        }
                        if (writingInst != null){
                            if (!markedInstructions.contains(writingInst)){
                                markedInstructions.add(writingInst);
                                workList.add(writingInst);
                                System.err.println(critInst + " Marking instruction - BB: " + writingInst);
                            } else {
                                System.err.println(critInst + " Found instruction - BB: " + writingInst);
                            }
                            continue;
                        }
                    // 2. check within the IN set of the block
                        for (IRInstruction instr : inSet){
                            if (markedInstructions.contains(instr)){
                                continue;
                            }
                            if (isWriting(instr, critOp[i])){
                                markedInstructions.add(instr);
                                workList.add(instr);
                                System.err.println(critInst + " Marking instruction - IN: " + instr);
                            }
                        }
                    }
                    break;
                default:
                    System.out.println("Error: Invalid opcode");    
                    break;
                    // do nothing, error
            }
        }
        System.err.println("End of marking");
    }


    private void markDeadCode_linear() {
        // clear the marked instructions
        markedInstructions.clear();
        // find critical instructions and mark them
        for (IRFunction function : program.functions) {

            List <IRInstruction> workList = new ArrayList<>();
            for (IRInstruction instruction : function.getInstructions()) {         
                if (isCrit(instruction)) {
                    markedInstructions.add(instruction);
                    workList.add(instruction);
                }
            }


            // iterate over the worklist and mark all instructions 
            while (!workList.isEmpty()) {
                IRInstruction instruction = workList.remove(0);
                IROperand[] currOp = instruction.operands;
                // find instrcutiosn that use the operand 2nd onwards and mark them     
                System.err.println("Current instruction: " + instruction.opCode);
                switch (instruction.opCode){
                    case GOTO:
                    case LABEL:
                        break;
                    
                    case ASSIGN:
                    case CALL: // the ops could be an array
                        // check the 2nd onwards 
                    case BREQ:
                    case BRNEQ:
                    case BRLT:
                    case BRGT:
                    case BRLEQ:
                    case BRGEQ:
                        // find the instruction that uses the 2nd onwards 
                    case ADD:
                    case SUB:
                    case MULT:
                    case DIV:
                    case AND:
                    case OR:
                        // check the 2nd and 3rd op
                        System.err.println("    2nd and 3rd onwards");
                        for (IRInstruction instr : function.getInstructions()) {
                            System.err.print("    Checking instruction: " + instr.opCode);
                            // if the instrcution not marked 
                            if (markedInstructions.contains(instr)){
                                System.err.println("    This instruction already marked");
                                continue;
                            }

                            for (int i = 1; i < currOp.length; i++) {
                                if (isWriting(instr, currOp[i])) {
                                    System.err.println("    This instruction was marked with" + currOp[i]);
                                    markedInstructions.add(instr);
                                    workList.add(instr);
                                    break;
                                }
                            }
                        }
                        break;
                            
                    case RETURN:
                        // check the first op 
                        System.err.println("    1st only");
                        for (IRInstruction instr : function.getInstructions()) {
                            System.err.print("    Checking instruction: " + instr.opCode);
                            // System.out.println("Checking instruction: " + instr.opCode + " for " + currOp[0]); 
                            if (markedInstructions.contains(instr)) 
                            {
                                // System.out.println("This Instruction already marked");
                                System.err.println("    This instruction already marked");
                                continue;
                            }
                            if (isWriting(instr, currOp[0])) {
                                // System.out.println("This Instruction has been marked");
                                System.err.println("    This instruction was marked with" + currOp[0]);
                                markedInstructions.add(instr);
                                workList.add(instr);
                            }
                        }
                        break;
                    
                    case CALLR:
                        // check the 3nd onwards
                    case ARRAY_LOAD:
                        // check the 3rd op
                        System.err.println("    3rd only");
                        for (IRInstruction instr : function.getInstructions()) {

                            if (markedInstructions.contains(instr)) 
                            {
                                // System.out.println("This Instruction already marked");
                                System.err.println("    This instruction already marked");
                                continue;
                            }


                            for (int i = 2; i < currOp.length; i++) {
                                if (isWriting(instr, currOp[i])) {
                                    System.err.println("    This instruction was marked with" + currOp[i]);
                                    markedInstructions.add(instr);
                                    workList.add(instr);
                                    break;
                                }

                            }
                        }
                        break;
                    
                    case ARRAY_STORE:
                    // check the 1st and 3rd op
                        System.err.println("    1st and 3rd");
                        for (IRInstruction instr : function.getInstructions()) {
                            if (markedInstructions.contains(instr)) 
                            {
                                // System.out.println("This Instruction already marked");
                                System.err.println("    This instruction already marked");
                                continue;
                            }

                            if (isWriting(instr, currOp[0]) || isWriting(instr, currOp[2])) {
                                System.err.println("    This instruction was marked with" + currOp[0] + " or "+ currOp[2]);
                                markedInstructions.add(instr);
                                workList.add(instr);
                            }
                        }
                        break;

                    default:
                        System.out.println("Error: Invalid opcode");    
                        break;
                        // do nothing, error
                }
            }
        }
        
    }

    private boolean isCrit (IRInstruction instruction) {
        switch (instruction.opCode) {
            case GOTO:
            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRLEQ:
            case BRGEQ:
            case RETURN:
            case CALL:
            case CALLR:
            case LABEL:
                return true;
            default:
                return false;
        }
    }

    private boolean isWriting (IRInstruction instruction, IROperand operand) {
        // System.err.println("Checking if " + instruction.opCode + " with list" + instruction.operands + " writes to " + operand);
        if (!(operand instanceof IRVariableOperand)) {
            // System.err.println("Operand is not a variable!!!");
            return false;
        }
        switch (instruction.opCode) {
            case ARRAY_STORE:
                return operand.toString() == instruction.operands[1].toString();
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
            case CALLR:
            case ARRAY_LOAD:
            case ASSIGN:
                boolean result = operand.toString() == instruction.operands[0].toString();
                // System.err.println("Checking if " + instruction.operands[0] + " equals " + operand + " " + result);
                return result;  
            default:
                return false;
        }
    }

    private void eliminateDeadCode() {
        // Eliminate all dead code from the program
        for (IRFunction function : program.functions) {
            List<IRInstruction> instructions = function.getInstructions();
            List<IRInstruction> newInstructions = new ArrayList<>();
            for (IRInstruction instruction : instructions) {
                if (markedInstructions.contains(instruction)) {
                    newInstructions.add(instruction);
                }
            }
            function.setInstructions(newInstructions);
        }
    }


    private void eliminateDeadCode(IRFunction function) {
        // Eliminate all dead code from the program
            List<IRInstruction> instructions = function.getInstructions();
            List<IRInstruction> newInstructions = new ArrayList<>();
            for (IRInstruction instruction : instructions) {
                if (markedInstructions.contains(instruction)) {
                    newInstructions.add(instruction);
                }
            }
            function.setInstructions(newInstructions);
    }

    void dump_in_out(){
        for (CFG cfg : INs.keySet()){
            System.err.println("Function: " + cfg.function.name);
            System.err.println("IN sets: ");
            int cnt = 0;
            for (BasicBlock bb : INs.get(cfg).keySet()){
                System.err.print("BB: " + bb.getStartLine() + " || ");
                for (IRInstruction instr : INs.get(cfg).get(bb)){
                    System.err.print(instr.irLineNumber + ", ");
                }
                System.err.println("");
                cnt++;
            }

            cnt = 0;
            System.err.println("OUT sets: ");
            for (BasicBlock bb : OUTs.get(cfg).keySet()){
                System.err.print("BB: " + bb.getStartLine() + " || ");
                for (IRInstruction instr : OUTs.get(cfg).get(bb)){
                    System.err.print(instr.irLineNumber + ", ");
                }
                System.err.println("");
                cnt++;
            }
        }
    }

}
