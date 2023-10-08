package com.game;

public class ValueOperationPair {

    private Cell.Operation operation;

    private Double value;

    public ValueOperationPair(Cell.Operation operation, Double value) {
        this.operation = operation;
        this.value = value;
    }

    public Cell.Operation getOperation() {
        return operation;
    }

    public void setOperation(Cell.Operation operation) {
        this.operation = operation;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
