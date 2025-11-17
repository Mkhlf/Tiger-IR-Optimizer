# Tiger Compiler Performance Comparison: Naive vs Greedy Code Generation

## Test Results Summary

### 1. Register Pressure Test
- **Naive approach**: 178 total instructions, 82 memory operations (lw/sw)
- **Greedy approach**: 199 total instructions, 54 memory operations (lw/sw)
- **Memory Access Reduction**: 34% fewer memory operations
- **Note**: The greedy approach generates more instructions but significantly reduces expensive memory access

### Key Differences Observed:

#### Naive Approach:
```mips
# Load from memory for operations
lw $t0, 12($sp)    # Load a
lw $t1, 16($sp)    # Load b  
add $t2, $t0, $t1  # Add
sw $t2, 20($sp)    # Store result
```

#### Greedy Approach:
```mips
# Keep values in registers when possible
li $t1, 1          # a in register
li $t3, 2          # b in register
add $t7, $t1, $t3  # Direct register operation
# Store only when necessary
```

### Performance Trade-offs:

1. **Instruction Count**: Greedy may have more instructions due to:
   - Additional register management code
   - Comments for debugging (in this implementation)
   - Register allocation overhead

2. **Memory Access**: Greedy approach reduces memory access by:
   - Keeping frequently used variables in registers
   - Using register-to-register operations
   - Reducing load/store operations

3. **Real Performance**: Despite higher instruction count, greedy is typically faster because:
   - Memory access is much slower than register operations
   - Modern CPUs can execute register operations in parallel
   - Cache misses are avoided

## Optimization Observations

The optimizer successfully:
- Performs dead code elimination
- Optimizes control flow
- Reduces redundant computations

Example from test output:
- Quicksort: 35% reduction in memory operations (as documented)
- Variable usage optimization in complex expressions

## Conclusion

While the greedy approach may generate more total instructions, it produces more efficient code by:
1. Maximizing register usage
2. Minimizing memory access
3. Enabling better CPU pipeline utilization

The actual performance gain would be visible in execution time rather than pure instruction count.
