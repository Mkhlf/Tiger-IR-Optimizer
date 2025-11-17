package ir;

import ir.datatype.IRType;
import ir.operand.IRVariableOperand;

import java.util.List;

public class IRFunction {

    public String name;

    public IRType returnType;

    public List<IRVariableOperand> parameters;

    public List<IRVariableOperand> variables;

    public List<IRInstruction> instructions;

    public IRFunction(String name, IRType returnType,
                      List<IRVariableOperand> parameters, List<IRVariableOperand> variables,
                      List<IRInstruction> instructions) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.variables = variables;
        this.instructions = instructions;
    }

    public String toString() {
        return name;
    }

    public List<IRVariableOperand> getVarOnly (){
        // return a list of variables that are not parameters
        List<IRVariableOperand> varOnly = variables;
        varOnly.removeAll(parameters);
        return varOnly;
    }

    public List<IRInstruction> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<IRInstruction> instructions) {
        this.instructions = instructions;
    }
}
