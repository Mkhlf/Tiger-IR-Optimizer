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
        java -cp "antlr-4.12.0-complete.jar:build/frontend:build/optimizer" TigerDriver "$INPUT_FILE"
        ;;
    
    optimize)
        echo "Optimizing $INPUT_FILE..."
        java -cp build/optimizer middle_end.midEnd "$INPUT_FILE" > out.ir
        echo "Output written to out.ir"
        ;;
    
    codegen)
        echo "Generating MIPS code for $INPUT_FILE..."
        # Default to naive unless specified otherwise
        SELECTOR="--naive"
        if [ "$#" -ge 3 ] && [ "$3" == "--greedy" ]; then
            SELECTOR="--greedy"
        fi
        java -cp build/backend BackEnd "$INPUT_FILE" "$SELECTOR"
        echo "Output written to out.s"
        ;;
    
    compile)
        echo "Full compilation of $INPUT_FILE..."
        
        # Parse to IR
        echo "Step 1: Parsing and generating IR..."
        java -cp "antlr-4.12.0-complete.jar:build/frontend:build/optimizer" TigerDriver "$INPUT_FILE"
        
        # Check if IR was generated
        if [ -f "temp.ir" ]; then
            echo "Step 2: Optimizing..."
            java -cp build/optimizer middle_end.midEnd temp.ir > out.ir
            
            echo "Step 3: Generating MIPS..."
            java -cp build/backend BackEnd out.ir --naive
            
            echo "Compilation complete! Output in out.s"
        else
            echo "Error: IR generation failed"
        fi
        ;;
    
    *)
        echo "Unknown command: $COMMAND"
        exit 1
        ;;
esac
