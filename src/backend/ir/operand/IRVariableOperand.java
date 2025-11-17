package ir.operand;

import ir.IRInstruction;
import ir.datatype.IRType;

public class IRVariableOperand extends IROperand {

    public IRType type;

    public IRVariableOperand(IRType type, String name, IRInstruction parent) {
        super(name, parent);
        this.type = type;
    }

    public String getName() {
        return value;
    }

    public IRType getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override 
    public boolean equals(Object obj) {
        if (obj instanceof IRVariableOperand) {
            IRVariableOperand other = (IRVariableOperand) obj;
            return getName().equals(other.getName());
        }
        return false;
    }
}
