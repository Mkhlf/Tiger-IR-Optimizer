package ir.operand;

import ir.IRInstruction;

public abstract class IROperand {

    protected String value;

    protected IRInstruction parent;

    public IROperand(String value, IRInstruction parent) {
        this.value = value;
        this.parent = parent;
    }

    public IRInstruction getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof IROperand)) {
            return false;
        }
        IROperand operand = (IROperand) obj;
        return operand.value.equals(value);
    }
}
