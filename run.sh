#!/bin/bash

# Write a script to run your optimizer in this file 
# This script should take one command line argument: an path to 
# an input ir file as 
# This script should output an optimized ir file named "out.ir"

java -cp ./build middle_end.midEnd ./materials/public_test_cases/sqrt/sqrt.ir > out.ir 2> err.txt
# java -cp ./build middle_end.midEnd $1 > out.ir 2> err.txt

# java -cp ./build IRInterpreter out.ir 
# java -cp ./build IRInterpreter ./materials/example/example.ir

# path for 1st test case: materials/public_test_cases/quicksort/quickSort.ir
# input for 1st test case: materials/public_test_cases/quicksort/*.in
# output for 1st test case: materials/public_test_cases/quicksort/*.out


# use a counter 
counter=0
for input_file in materials/public_test_cases/sqrt/*.in; do
    echo "Running optimized IR with input: $input_file"
    # output to counter-opt.out
    java -cp ./build IRInterpreter out.ir < $input_file > ${input_file%}-opt.out
    echo "Output generated: ${input_file%.in}.out"
    # check the difference between the output and the expected output
    diff ${input_file%}-opt.out ${input_file%}.out
done