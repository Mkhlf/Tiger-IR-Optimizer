#!/bin/bash

# Tiger Compiler Run Script

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <command> <input_file>"
    echo ""
    echo "Commands:"
    echo "  parse <file.tiger>     - Parse Tiger source code"
    echo "  optimize <file.ir>     - Optimize IR code"
    echo "  codegen <file.ir>      - Generate MIPS assembly"
    echo "  compile <file.tiger>   - Full compilation pipeline"
    echo ""
    exit 1
fi

COMMAND=$1
INPUT_FILE=$2

# Check if build directory exists
if [ ! -d "build" ]; then
    echo "Build directory not found. Running build script..."
    ./build.sh
fi

case $COMMAND in
    parse)
        echo "Parsing $INPUT_FILE..."
        java -cp "antlr-4.12.0-complete.jar:build/frontend" TigerDriver "$INPUT_FILE"
        ;;
    
    optimize)
        echo "Optimizing $INPUT_FILE..."
        java -cp build/optimizer Demo "$INPUT_FILE"
        echo "Output written to out.ir"
        ;;
    
    codegen)
        echo "Generating MIPS code for $INPUT_FILE..."
        java -cp build/backend BackEnd "$INPUT_FILE"
        echo "Output written to out.s"
        ;;
    
    compile)
        echo "Full compilation of $INPUT_FILE..."
        
        # Parse to IR (would need to be implemented to output IR)
        echo "Step 1: Parsing..."
        java -cp "antlr-4.12.0-complete.jar:build/frontend" TigerDriver "$INPUT_FILE"
        
        # For now, assume the parser outputs to temp.ir
        # In a real implementation, the parser would generate IR
        
        if [ -f "temp.ir" ]; then
            echo "Step 2: Optimizing..."
            java -cp build/optimizer Demo temp.ir
            
            echo "Step 3: Generating MIPS..."
            java -cp build/backend BackEnd out.ir
            
            echo "Compilation complete! Output in out.s"
        else
            echo "Note: Full pipeline requires parser to generate IR (not yet implemented)"
        fi
        ;;
    
    *)
        echo "Unknown command: $COMMAND"
        exit 1
        ;;
esac
