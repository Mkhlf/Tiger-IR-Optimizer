#!/bin/bash

# Tiger Compiler Build Script

echo "Building Tiger Compiler..."

# Create build directories
mkdir -p build/frontend build/optimizer build/backend

# Build Optimizer first (contains IR classes needed by frontend)
echo "Building IR Optimizer..."
javac src/optimizer/ir/*.java src/optimizer/ir/datatype/*.java src/optimizer/ir/operand/*.java src/optimizer/middle_end/*.java src/optimizer/*.java -d build/optimizer

# Build Frontend Parser
echo "Building Frontend Parser..."
if [ ! -f "antlr-4.12.0-complete.jar" ]; then
    echo "Downloading ANTLR..."
    curl -O https://www.antlr.org/download/antlr-4.12.0-complete.jar
fi

# Generate ANTLR parser with visitor support
java -Xmx500M -cp "./antlr-4.12.0-complete.jar:$CLASSPATH" org.antlr.v4.Tool src/frontend/tiger.g4 -visitor -o build/frontend

# Move generated files to correct package directory
mkdir -p build/frontend/antlr_generated
if [ -d "build/frontend/src/frontend" ]; then
    mv build/frontend/src/frontend/*.java build/frontend/antlr_generated/
    rm -rf build/frontend/src
fi

# Compile frontend with IR support
javac -cp "./antlr-4.12.0-complete.jar:build/frontend:build/optimizer:$CLASSPATH" build/frontend/antlr_generated/*.java src/frontend/TigerDriver.java src/frontend/TigerIRGenerator.java -d build/frontend

# Build Backend
echo "Building Backend MIPS32 Generator..."
javac src/backend/ir/*.java src/backend/ir/datatype/*.java src/backend/ir/operand/*.java src/backend/*.java -d build/backend

echo "Build complete!"
echo ""
echo "Usage:"
echo "  Frontend: java -cp 'antlr-4.12.0-complete.jar:build/frontend' TigerDriver <input.tiger>"
echo "  Optimizer: java -cp build/optimizer middle_end.midEnd <input.ir> > out.ir"
echo "  Backend: java -cp build/backend BackEnd <input.ir> --naive"
