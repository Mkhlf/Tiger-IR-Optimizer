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

    Optimizer(IRProgram program) {
        this.program = program;
        this.markedInstructions = new HashSet<>();
    }

    void optimize() {
        // ceate cfg for each function
        // compute reaching definitions within each function cfg 
        // mark dead code in each function
        // eliminate dead code in each function // all app 
        for (IRFunction function : program.functions) {
            CFG cfg = new CFG(function);
            computeReachingDefinitions(cfg);
            markDeadCode();
            // eliminateDeadCode();
        }
        markDeadCode_linear();
        eliminateDeadCode();
    }

    // private void computeReachingDefinitions() {
    //     // Find all reaching definitions for each variable

    // }

    private void computeReachingDefinitions (CFG cfg){
        // initialize the reaching definitions for each block
        IRFunction function = cfg.function;
        Map <BasicBlock, Set<IRInstruction>> gen = new HashMap<>();  // set of definitions(instructions) generated in the block
        Map <BasicBlock, Set<IRInstruction>> kill = new HashMap<>(); // set of definitions that are might be killed by other blocks
        
        // compute gen and kill sets for each block
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
                IROperand defOp = def.operands[0]; // left hand side of the definition
                if (def.opCode == OpCode.ARRAY_STORE){
                    defOp = def.operands[1];
                }
                for (IRInstruction currInstruction : function.getInstructions()){
                    if (bb.getInstructions().contains(currInstruction)){
                        continue;
                    }
                    IROperand currOp;
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
                            currOp = currInstruction.operands[0];
                            if (currOp.equals(defOp)){
                                newKill.add(currInstruction);
                            }
                            break;

                        case ARRAY_STORE:
                            currOp = currInstruction.operands[1];
                            if (currOp.equals(defOp)){
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
        boolean changed = true;
        while (changed){
            changed = false;
            for (BasicBlock bb : cfg.basicBlocks){
                // in = union of all predecessors out
                Set <IRInstruction> newIn = new HashSet<>();
            
                for (BasicBlock pred : bb.getPredecessors()){
                    newIn.addAll(out.get(pred));
                }
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

        // if the instruction is not in the out set, mark it as dead

        
    }

    private void markDeadCode() {
        // 
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
        System.err.println("Checking if " + instruction.opCode + " with list" + instruction.operands + " writes to " + operand);
        if (!(operand instanceof IRVariableOperand)) {
            // System.err.println("Operand is not a variable!!!");
            return false;
        }
        switch (instruction.opCode) {
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
            case CALLR:
            case ARRAY_LOAD:
            case ASSIGN:
            case ARRAY_STORE:
                boolean result = operand.toString() == instruction.operands[0].toString();
                System.err.println("Checking if " + instruction.operands[0] + " equals " + operand + " " + result);
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

}
