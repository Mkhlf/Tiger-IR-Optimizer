import antlr_generated.*;
import ir.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;


public class TigerDriver {
    public static void main(String[] args) {
        try {
            // Check if the file path is provided
            if (args.length == 0) {
                System.err.println("Usage: java TigerDriver <path_to_tiger_program>");
                return;
            }

            // Read the input file
            String inputFile = args[0];
            CharStream input = CharStreams.fromFileName(inputFile);

            // Create a lexer that feeds off of input CharStream
            tigerLexer lexer = new tigerLexer(input);

            // Create a buffer of tokens pulled from the lexer
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Create a parser that feeds off the tokens buffer
            tigerParser parser = new tigerParser(tokens);

            // Replace default error listener with custom one
            parser.removeErrorListeners();
            parser.addErrorListener(new VerboseErrorListener());

            // Begin parsing at tiger_program rule, which is the start symbol
            ParseTree tree = parser.prog();

            if (parser.getNumberOfSyntaxErrors() == 0) {
                System.out.println("successful");
                
                // Generate IR
                TigerIRGenerator irGen = new TigerIRGenerator();
                irGen.visit(tree);
                IRProgram program = irGen.getProgram();
                
                String outputFile = "temp.ir";
                if (args.length > 1) {
                    outputFile = args[1];
                }
                
                // Write IR to file
                try (PrintStream ps = new PrintStream(new FileOutputStream(outputFile))) {
                    IRPrinter printer = new IRPrinter(ps);
                    printer.printProgram(program);
                    System.out.println("IR generated to: " + outputFile);
                } catch (IOException e) {
                    System.err.println("Error writing IR file: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static class VerboseErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e) {
            System.err.println("Error in line " + line + ":" + charPositionInLine + " - " + msg);
        }
    }
}