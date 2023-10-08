package com.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ScratchGame {

    public static void main(String[] args) {
        //reading parameters
        double bettingAmount = 0;
        String config = "";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config"))
                config = args[i + 1];
            if (args[i].equals("--betting-amount"))
                bettingAmount = Double.parseDouble(args[i + 1]);
        }
        if (config.isEmpty())
            return;

        double bet = bettingAmount;

        try (Reader reader = Files.newBufferedReader(Paths.get(config))) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode parser = objectMapper.readTree(reader);

            Cell[][] field = new Cell[parser.get("columns").asInt()][parser.get("rows").asInt()];

            Map<String, ValueOperationPair> symbols = getSymbolsWithOperation(parser);

            Map<String, Integer> symbolCounters = new HashMap<>();
            Map<String, Integer> bonusSymbolCounters = new HashMap<>();
            fillFieldByCells(parser, field, symbols, symbolCounters, bonusSymbolCounters);

            Map<String, List<String>> appliedWinningCombinations = new HashMap<>();
            Map<Integer, Double> sameSymbolsWinMap = new HashMap<>();
            Map<String, Map<Double, List<List<List<String>>>>> linearSymbolsWinMap = new HashMap<>();
            //win conditions parsing
            for (JsonNode c : parser.path("win_combinations")) {
                if (c.get("when").asText().equals("same_symbols"))
                    sameSymbolsWinMap.put(c.get("count").asInt(), Double.parseDouble(c.get("reward_multiplier").asText()));
                else if (c.get("when").asText().equals("linear_symbols")) {
                    List<List<List<String>>> linearGroup = new ArrayList<>();
                    linearGroup.add(objectMapper.readValue(c.get("covered_areas").toString(), new TypeReference<>() {}));
                    linearSymbolsWinMap
                            .computeIfAbsent(c.get("group").asText(), k -> new HashMap<>())
                            .put(Double.parseDouble(c.get("reward_multiplier").asText()), linearGroup);
                }
            }

            //calculating multiplication bonus of symbols and same symbol win conditions
            List<Integer> countWins = sameSymbolsWinMap.keySet().stream().sorted().toList();
            Map<String, Double> sameSymbolScoreMap = new HashMap<>();
            symbolCounters.forEach((key, value) -> {
                if (countWins.contains(value)) {
                    sameSymbolScoreMap.put(key, sameSymbolsWinMap.get(value) * symbols.get(key).getValue());
                    appliedWinningCombinations.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(String.format("same_symbol_%s_times", value));
                }
                else if (countWins.get(countWins.size() - 1) < value) {//same symbol count is more than max win condition
                    sameSymbolScoreMap.put(key, sameSymbolsWinMap.get(countWins.get(countWins.size() - 1)) * value * symbols.get(key).getValue());
                    appliedWinningCombinations.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(String.format("same_symbol_%s_times", countWins.get(countWins.size() - 1)));
                }
                else if (countWins.get(0) < value) {//same symbol count is more than min win condition but is not explicitly represented
                    int nearestSmall = value;
                    while (!countWins.contains(nearestSmall))
                        nearestSmall--;
                    sameSymbolScoreMap.put(key, sameSymbolsWinMap.get(nearestSmall) * value * symbols.get(key).getValue());
                    appliedWinningCombinations.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(String.format("same_symbol_%s_times", nearestSmall));
                }
            });

            //calculating multiplication bonus of linear win conditions for symbols
            // in assuming that every single symbol can hit one condition once
            Map<String, Double> linearSymbolScoreMap = new HashMap<>();
            linearSymbolsWinMap.forEach((grpName, grpWithScore) -> grpWithScore.forEach((key, value) -> value.forEach(list -> {
                for (List<String> l : list) {
                    String tempSymbol = "";
                    boolean isSameRow = true;
                    for (String s : l) {
                        String[] spltd = s.trim().split(":");
                        int x = Integer.parseInt(spltd[0]);
                        int y = Integer.parseInt(spltd[1]);
                        String cellSymbol = field[x][y].getSymbol();
                        if (tempSymbol.isEmpty())
                            tempSymbol = cellSymbol;
                        if (!tempSymbol.equals(cellSymbol) || bonusSymbolCounters.containsKey(cellSymbol)) {
                            isSameRow = false;
                            break;
                        }
                    }
                    if (isSameRow) {
                        linearSymbolScoreMap.put(tempSymbol, linearSymbolScoreMap.getOrDefault(tempSymbol, 1.0) * key);
                        appliedWinningCombinations.computeIfAbsent(tempSymbol, k -> new ArrayList<>()).add(grpName);
                        break;
                    }
                }
            })));

            //score calculation starts from same symbol win conditions because linear win conditions are unreachable
            // if there are less than 3 same symbols
            double total = sameSymbolScoreMap.entrySet().stream()
                    .map(e -> e.getValue() * linearSymbolScoreMap.getOrDefault(e.getKey(), 1.0) * bet)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            total = applyBonusesToScore(symbols, bonusSymbolCounters, total);

            String[][] matrix = getSymbolMatrixByField(field);

            OutputDTO outputDTO = new OutputDTO();
            outputDTO.setMatrix(matrix);
            outputDTO.setReward(total);
            outputDTO.setApplied_bonus_symbols(bonusSymbolCounters.keySet().stream().toList());
            outputDTO.setApplied_winning_combinations(appliedWinningCombinations);

            System.out.println(objectMapper.writeValueAsString(outputDTO));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, ValueOperationPair> getSymbolsWithOperation(JsonNode parser) {
        Map<String, ValueOperationPair> symbols = new HashMap<>();
        symbols.put("MISS", new ValueOperationPair(Cell.Operation.MULTIPLICATION, 1.0));

        parser.get("symbols").fields().forEachRemaining(jsonNode -> jsonNode.getValue().fields().forEachRemaining(e -> {
            if (e.getKey().equals("reward_multiplier"))
                symbols.put(jsonNode.getKey(), new ValueOperationPair(Cell.Operation.MULTIPLICATION, Double.parseDouble(e.getValue().asText())));
            else if (e.getKey().equals("extra"))
                symbols.put(jsonNode.getKey(), new ValueOperationPair(Cell.Operation.ADDITION, Double.parseDouble(e.getValue().asText())));
        }));
        return symbols;
    }

    private static void fillFieldByCells(JsonNode parser,
                                         Cell[][] field,
                                         Map<String, ValueOperationPair> symbols,
                                         Map<String, Integer> symbolCounters,
                                         Map<String, Integer> bonusSymbolCounters) {
        for (JsonNode s : parser.path("probabilities").path("standard_symbols")) {
            Cell cell = new Cell();
            if (ThreadLocalRandom.current().nextInt(0, 10) < 2) {//probability that symbol will become a bonus ~<20%
                cell.setSymbol(generateBonusSymbolByProbability(parser));
                bonusSymbolCounters.put(cell.getSymbol(), bonusSymbolCounters.getOrDefault(cell.getSymbol(), 0) + 1);
            } else {
                cell.setSymbol(generateNormalSymbolByProbability(s));
                symbolCounters.put(cell.getSymbol(), symbolCounters.getOrDefault(cell.getSymbol(), 0) + 1);
            }
            cell.setValue(symbols.get(cell.getSymbol()).getValue());
            cell.setOperation(symbols.get(cell.getSymbol()).getOperation());
            field[s.get("column").asInt()][s.get("row").asInt()] = cell;
        }
    }

    private static String generateBonusSymbolByProbability(JsonNode parser) {
        Map<Integer, String> bonusProbMap = new HashMap<>();
        parser.get("probabilities").get("bonus_symbols").get("symbols").fields()
                .forEachRemaining(e -> bonusProbMap.put(e.getValue().asInt(), e.getKey()));
        return generateSymbolByProbability(bonusProbMap);
    }

    private static String generateNormalSymbolByProbability(JsonNode parser) {
        Map<Integer, String> probMap = new HashMap<>();
        parser.get("symbols").fields().forEachRemaining(e -> probMap.put(e.getValue().asInt(), e.getKey()));
        return generateSymbolByProbability(probMap);
    }

    private static String generateSymbolByProbability(Map<Integer, String> probMap) {
        List<Integer> probabilities = probMap.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        int randomized = ThreadLocalRandom.current().nextInt(1, probMap.keySet().stream().mapToInt(Integer::intValue).sum() + 1);
        int tempSum = 0;
        int calculated = 0;
        for (int p : probabilities) {
            tempSum += p;
            if (tempSum >= randomized) {
                calculated = p;
                break;
            }
        }
        return probMap.get(calculated);
    }

    private static Double applyBonusesToScore(Map<String, ValueOperationPair> symbols,
                                              Map<String, Integer> bonusSymbolCounters,
                                              Double score) {
        for (Map.Entry<String, Integer> e : bonusSymbolCounters.entrySet()) {
            if (symbols.get(e.getKey()).getOperation().equals(Cell.Operation.MULTIPLICATION))
                for (int i = 1; i <= e.getValue(); i++)
                    score *= symbols.get(e.getKey()).getValue();
            else score += symbols.get(e.getKey()).getValue() * e.getValue();
        }
        return score;
    }

    private static String[][] getSymbolMatrixByField(Cell[][] field) {
        String[][] matrix = new String[field.length][field[0].length];
        for (int i = 0; i < field.length; i++)
            for (int j = 0; j < field[i].length; j++)
                matrix[i][j] = field[i][j].getSymbol();
        return matrix;
    }
}
