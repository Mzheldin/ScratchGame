package com.game;

import java.util.List;
import java.util.Map;

public class OutputDTO {

    private String[][] matrix;

    private Double reward;

    private Map<String, List<String>> applied_winning_combinations;

    private List<String> applied_bonus_symbols;

    public String[][] getMatrix() {
        return matrix;
    }

    public void setMatrix(String[][] matrix) {
        this.matrix = matrix;
    }

    public Double getReward() {
        return reward;
    }

    public void setReward(Double reward) {
        this.reward = reward;
    }

    public Map<String, List<String>> getApplied_winning_combinations() {
        return applied_winning_combinations;
    }

    public void setApplied_winning_combinations(Map<String, List<String>> applied_winning_combinations) {
        this.applied_winning_combinations = applied_winning_combinations;
    }

    public List<String> getApplied_bonus_symbols() {
        return applied_bonus_symbols;
    }

    public void setApplied_bonus_symbols(List<String> applied_bonus_symbols) {
        this.applied_bonus_symbols = applied_bonus_symbols;
    }
}
