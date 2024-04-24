package middle_end;

import ir.*;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import ir.datatype.IRType;
import ir.operand.IRConstantOperand;
import ir.operand.IROperand;
import ir.operand.IRVariableOperand;

import java.io.PrintStream;
import java.util.*;

import middle_end.*;

// main class
public class midEnd {
    public static void main(String[] args) throws Exception{
        // read program, optimize, and print optimized program
        IRReader irReader = new IRReader();
        IRProgram program = irReader.parseIRFile(args[0]);

        Optimizer optimizer = new Optimizer(program);
        optimizer.optimize();

        IRPrinter filePrinter = new IRPrinter(new PrintStream(System.out));
        filePrinter.printProgram(program);
    }
}
