package com.swingtrade.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic, bounded Cartesian product used by analytics experiments. */
public final class BoundedParameterGrid {
    private BoundedParameterGrid() { }

    public static List<Map<String, Object>> expand(Map<String, ? extends List<?>> ranges,
                                                    int maxCandidates) {
        if (ranges == null || ranges.isEmpty()) {
            return List.of(Map.of());
        }
        if (maxCandidates < 1) throw new IllegalArgumentException("maxCandidates must be positive");
        List<Map<String, Object>> result = new ArrayList<>();
        expand(new ArrayList<>(new TreeMap<>(ranges).entrySet()), 0, new TreeMap<>(), result,
            maxCandidates);
        return List.copyOf(result);
    }

    private static void expand(List<Map.Entry<String, ? extends List<?>>> entries, int index,
                               Map<String, Object> current, List<Map<String, Object>> result,
                               int maxCandidates) {
        if (result.size() >= maxCandidates) {
            throw new IllegalArgumentException("Parameter grid exceeds maxCandidates=" + maxCandidates);
        }
        if (index == entries.size()) {
            result.add(Collections.unmodifiableMap(new TreeMap<>(current)));
            return;
        }
        var entry = entries.get(index);
        if (entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue().isEmpty()) {
            throw new IllegalArgumentException("Parameter names and values are required");
        }
        for (Object value : entry.getValue()) {
            if (value == null) throw new IllegalArgumentException("Parameter values cannot be null");
            current.put(entry.getKey(), value);
            expand(entries, index + 1, current, result, maxCandidates);
        }
        current.remove(entry.getKey());
    }
}
