// package middle_end;

// import ir.*;
// import ir.operand.*;

// import java.util.*;

// class BasicBlock {
// private
//     List<IRInstruction> instructions;
// private
//     List<BasicBlock> successors;
// private
//     List<BasicBlock> predecessors;
// private
//     boolean visited;

//     BasicBlock()
//     {
//         this.instructions = new ArrayList<>();
//         this.successors = new ArrayList<>();
//         this.predecessors = new ArrayList<>();
//         this.visited = false;
//     }

//     void addInstruction(IRInstruction instruction)
//     {
//         instructions.add(instruction);
//     }

//     void addSuccessor(BasicBlock successor)
//     {
//         successors.add(successor);
//     }

//     void addPredecessor(BasicBlock predecessor)
//     {
//         predecessors.add(predecessor);
//     }

//     List<IRInstruction> getInstructions()
//     {
//         return instructions;
//     }

//     List<BasicBlock> getSuccessors()
//     {
//         return successors;
//     }

//     List<BasicBlock> getPredecessors()
//     {
//         return predecessors;
//     }

//     void setVisited(boolean visited)
//     {
//         this.visited = visited;
//     }

//     boolean isVisited()
//     {
//         return visited;
//     }
// }

// class CFG {
//     IRFunction function;
//     List<BasicBlock> basicBlocks;
//     Map<IRInstruction, BasicBlock> instrToBlock;
//     Map<String, BasicBlock> labelToBlock;

//     ControlFlowGraph(IRFunction function)
//     {
//         this.function = function;
//         this.basicBlocks = new ArrayList<>();
//         this.instrToBlock = new HashMap<>();
//         this.labelToBlock = new HashMap<>();
//         buildCFG();
//     }

//     void buildCFG()
//     {
//         List<Integer> startLNs = new ArrayList<>();
//         List<Integer> endLNs = new ArrayList<>();
//         startLNs.add(0);

//         List<IRInstruction> inst = function.getInstructions();
//         int n = inst.size();

//         // leader instructions are:
//         // 1. the first instruction of the program/function
//         // 2. the target of a conditional or unconditional jump / goto
//         // 3. the instruction following a jump/goto
//         for (int i = 0 i < n; i++) {
//             // check if the instruction is a leader
//             IRInstruction instr = inst.get(i);
//             if (i == 0 || isLeader(instr)) {
//                 startLNs.add(i);
//             }
//         }
//     }

//     boolean isLeader (IRInstruction instr)
//     { 
//         // check if the instruction is a leader
//     }
// }
