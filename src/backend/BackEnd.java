import ir.*;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import ir.datatype.IRType;
import ir.operand.IRConstantOperand;
import ir.operand.IROperand;
import ir.operand.IRVariableOperand;

import java.io.PrintStream;
import java.util.*;
import java.io.FileOutputStream;

public class BackEnd {
    public static void main(String[] args) throws Exception {
        // Parse the IR file
        IRReader irReader = new IRReader();
        IRProgram program = irReader.parseIRFile(args[0]);


        // Create an IR printer that prints to stdout
        IRPrinter irPrinter = new IRPrinter(new PrintStream(System.out));

        // Use the InstructionSelector to transform IR to MIPS32 instructions
        // if ran with tag --greedy, use InstructionSelectorGreedy 
        // if ran with tag --naive use InstructionSelector

        List<String> mipsInstructions = null;
        if (args.length >= 2 && args[1].equals("--greedy")) {
            InstructionSelectorGreedy selector;
            selector = new InstructionSelectorGreedy(program);
            mipsInstructions = selector.selectInstructions();
        } else if (args.length >= 2 && args[1].equals("--naive")) {
            InstructionSelector selector;
            selector = new InstructionSelector(program);
            mipsInstructions = selector.selectInstructions();
        } else {
            System.out.println("Please provide a tag --greedy or --naive");
            System.exit(1);
        }

        String outputFileName = "out.s";

        if (args.length >= 3) {
            outputFileName = args[2];
        }
        
        // Write the MIPS32 instructions to a file
        try (PrintStream mipsFile = new PrintStream(new FileOutputStream(outputFileName))) {
            for (String instr : mipsInstructions) {
                mipsFile.println(instr);
            }
        }
    }
}
