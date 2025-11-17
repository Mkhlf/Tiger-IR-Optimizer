#!/bin/bash

# Tiger Compiler Performance Test Script
# Compares naive vs greedy code generation

echo "=== Tiger Compiler Performance Comparison ==="
echo "Comparing naive vs greedy instruction selection"
echo

# Function to count lines in assembly files
count_instructions() {
    # Count non-comment, non-empty lines
    grep -v "^#" "$1" | grep -v "^$" | grep -v "^\s*$" | wc -l
}

# Test 1: Quicksort
echo "Test 1: Quicksort Algorithm"
echo "------------------------"
echo "Optimizing quicksort.ir..."
java -cp build/optimizer middle_end.midEnd test/backend_tests/quicksort/quicksort.ir > quicksort_opt.ir 2>&1

echo "Generating MIPS with naive approach..."
java -cp build/backend BackEnd quicksort_opt.ir --naive naive_quicksort.s 2>&1
cp out.s naive_quicksort.s

echo "Generating MIPS with greedy approach..."
java -cp build/backend BackEnd quicksort_opt.ir --greedy greedy_quicksort.s 2>&1
cp out.s greedy_quicksort.s

naive_count=$(count_instructions naive_quicksort.s)
greedy_count=$(count_instructions greedy_quicksort.s)

echo "Naive approach: $naive_count instructions"
echo "Greedy approach: $greedy_count instructions"
echo "Reduction: $(( (naive_count - greedy_count) * 100 / naive_count ))%"
echo

# Test 2: Comprehensive test
echo "Test 2: Comprehensive Test (loops, arrays, conditionals)"
echo "------------------------"
echo "Generating IR from Tiger source..."
java -cp "antlr-4.12.0-complete.jar:build/frontend:build/optimizer" TigerDriver test/frontend_tests/correct/test_comprehensive.tiger > /dev/null 2>&1

echo "Optimizing temp.ir..."
java -cp build/optimizer middle_end.midEnd temp.ir > comprehensive_opt.ir 2>&1

echo "Generating MIPS with naive approach..."
java -cp build/backend BackEnd comprehensive_opt.ir --naive > /dev/null 2>&1
cp out.s naive_comprehensive.s

echo "Generating MIPS with greedy approach..."
java -cp build/backend BackEnd comprehensive_opt.ir --greedy > /dev/null 2>&1
cp out.s greedy_comprehensive.s

naive_count=$(count_instructions naive_comprehensive.s)
greedy_count=$(count_instructions greedy_comprehensive.s)

echo "Naive approach: $naive_count instructions"
echo "Greedy approach: $greedy_count instructions"
echo "Reduction: $(( (naive_count - greedy_count) * 100 / naive_count ))%"
echo

# Test 3: Register pressure test
echo "Test 3: Register Pressure Test"
echo "------------------------"
echo "Generating IR from Tiger source..."
java -cp "antlr-4.12.0-complete.jar:build/frontend:build/optimizer" TigerDriver test/frontend_tests/correct/test_register_pressure.tiger > /dev/null 2>&1

echo "Optimizing temp.ir..."
java -cp build/optimizer middle_end.midEnd temp.ir > simple_opt.ir 2>&1

echo "Generating MIPS with naive approach..."
java -cp build/backend BackEnd simple_opt.ir --naive > /dev/null 2>&1
cp out.s naive_simple.s

echo "Generating MIPS with greedy approach..."
java -cp build/backend BackEnd simple_opt.ir --greedy > /dev/null 2>&1
cp out.s greedy_simple.s

naive_count=$(count_instructions naive_simple.s)
greedy_count=$(count_instructions greedy_simple.s)

echo "Naive approach: $naive_count instructions"
echo "Greedy approach: $greedy_count instructions"
echo "Reduction: $(( (naive_count - greedy_count) * 100 / naive_count ))%"
echo

# Show a sample diff
echo "=== Sample Assembly Comparison (first 30 lines of Register Pressure test) ==="
echo "NAIVE:"
head -30 naive_simple.s | grep -v "^#"
echo
echo "GREEDY:"
head -30 greedy_simple.s | grep -v "^#"

# Clean up
rm -f quicksort_opt.ir comprehensive_opt.ir simple_opt.ir
