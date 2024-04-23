import ir.*;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import ir.datatype.IRType;
import ir.operand.IRConstantOperand;
import ir.operand.IROperand;
import ir.operand.IRVariableOperand;

import java.io.PrintStream;
import java.util.*;

// Reaching definitions class 
class ReachingDefinitions {
    String variableName;
    IRInstruction definition;
    int programPoint;

    ReachingDefinition(String variableName, IRInstruction definition, int programPoint) {
        this.variableName = variableName;
        this.definition = definition;
        this.programPoint = programPoint;
    }
}

// Optimizer class
class Optimizer {
    private IRProgram program;
    private Map<String, Set<ReachingDefinition>> reachingDefinitions;
    private Set<IRInstruction> markedInstructions;

    Optimizer(IRProgram program) {
        this.program = program;
        this.reachingDefinitions = new HashMap<>();
        this.markedInstructions = new HashSet<>();
    }

    void optimize() {
        // create flow graphs... 
        computeReachingDefinitions();
        markDeadCode();
        eliminateDeadCode();
    }

    private void computeReachingDefinitions() {
        // Find all reaching definitions for each variable

    }

    private void markDeadCode() {
        // Mark all dead code
        
    }

    private void eliminateDeadCode() {
        // Eliminate all dead code from the program
        for (IRFunction function : program.functions) {
            List<IRInstruction> instructions = function.getInstructions();
            List<IRInstruction> newInstructions = new ArrayList<>();
            for (IRInstruction instruction : instructions) {
                if (!markedInstructions.contains(instruction)) {
                    newInstructions.add(instruction);
                }
            }
            function.setInstructions(newInstructions);
        }
    }

}


// main class
public class midEnd {
    public static void main(String[] args) {
        // read program, optimize, and print optimized program
    }
}
