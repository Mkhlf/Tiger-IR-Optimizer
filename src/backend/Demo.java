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

public class Demo {
    public static void main(String[] args) throws Exception {
        // Parse the IR file
        IRReader irReader = new IRReader();
        IRProgram program = irReader.parseIRFile(args[0]);

        // Print the IR to another file
        IRPrinter filePrinter = new IRPrinter(new PrintStream(args[1]));
        filePrinter.printProgram(program);

        // Create an IR printer that prints to stdout
        IRPrinter irPrinter = new IRPrinter(new PrintStream(System.out));

        // Use the InstructionSelector to transform IR to MIPS32 instructions
        InstructionSelector selector = new InstructionSelector(program);
        List<String> mipsInstructions = selector.selectInstructions();

        // Write the MIPS32 instructions to a file
        try (PrintStream mipsFile = new PrintStream(new FileOutputStream("output.s"))) {
            for (String instr : mipsInstructions) {
                mipsFile.println(instr);
            }
        }

        // System.out.println("Generated MIPS32 instructions:");
        // for (String instr : mipsInstructions) {
        //     System.out.println(instr);
        // }
    }
}