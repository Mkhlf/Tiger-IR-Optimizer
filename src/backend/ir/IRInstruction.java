package ir;

import ir.operand.IROperand;

public class IRInstruction {

    public enum OpCode {
        ASSIGN,
        ADD, SUB, MULT, DIV, AND, OR,
        GOTO,
        BREQ, BRNEQ, BRLT, BRGT, BRLEQ, BRGEQ,
        RETURN,
        CALL, CALLR,
        ARRAY_STORE, ARRAY_LOAD,
        LABEL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public OpCode opCode;

    public IROperand[] operands;

    public int irLineNumber;

    public IRInstruction() {}

    public IRInstruction(OpCode opCode, IROperand[] operands, int irLineNumber) {
        this.opCode = opCode;
        this.operands = operands;
        this.irLineNumber = irLineNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof IRInstruction)) {
            return false;
        }
        IRInstruction inst = (IRInstruction) obj;
        return inst.irLineNumber == irLineNumber && inst.opCode.toString() == opCode.toString();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(opCode);
        sb.append(" ");
        for (int i = 0; i < operands.length; i++) {
            sb.append(operands[i]);
            if (i < operands.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // get target 
    public IROperand getTarget() {
        switch (this.opCode){
            case ASSIGN:
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
            case CALLR:
            case ARRAY_LOAD:
                return operands[0];
            case ARRAY_STORE:
            // 2nd op
                return operands[1];
            default:
                return null;
        }
    }

    // get sources
    public IROperand[] getSources() {
        switch (this.opCode){
            case RETURN:
                return new IROperand[]{operands[0]};
            case ASSIGN:
            // 2nd op
                return new IROperand[]{operands[1]};
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
            // 2nd and 3rd op
                return new IROperand[]{operands[1], operands[2]};
            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRLEQ:
            case BRGEQ:
            // 2nd and 3rd op
                return new IROperand[]{operands[1], operands[2]};

            
            case CALL:
            // 2nd op onwards
                IROperand[] sources = new IROperand[operands.length - 1];
                for (int i = 1; i < operands.length; i++) {
                    sources[i - 1] = operands[i];
                }
                return sources;
            case CALLR:
            // 3rd op onwards
                IROperand[] sources2 = new IROperand[operands.length - 2];
                for (int i = 2; i < operands.length; i++) {
                    sources2[i - 2] = operands[i];
                }
                return sources2;
            
            case ARRAY_LOAD:
            // 2nd and 3rd op
                return new IROperand[]{operands[1], operands[2]};
                    
            case ARRAY_STORE:
            // 1st and 3rd op
                return new IROperand[]{operands[0], operands[2]};
            default:
                return null;
        }
    }

}
