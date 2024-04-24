package middle_end;

import ir.*;
import ir.datatype.*;
import ir.operand.*;

import java.util.*;

// Optimizer class
class Optimizer {
    private IRProgram program;
    // private Map<String, Set<ReachingDefinition>> reachingDefinitions;
    private Set<IRInstruction> markedInstructions;

    Optimizer(IRProgram program) {
        this.program = program;
        // this.reachingDefinitions = new HashMap<>();
        this.markedInstructions = new HashSet<>();
    }

    void optimize() {
        // create flow graphs... 
        // computeReachingDefinitions();
        markDeadCode();
        eliminateDeadCode();
    }

    // private void computeReachingDefinitions() {
    //     // Find all reaching definitions for each variable

    // }

    private void markDeadCode() {
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
