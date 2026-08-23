package com.swingtrade.llm.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * Extracted earnings data from PDF documents.
 */
public record EarningsData(
    String symbol,
    String quarter,
    BigDecimal revenue,
    BigDecimal netProfit,
    BigDecimal eps,
    BigDecimal ebitda,
    String guidance,
    LocalDate extractionDate
) {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static EarningsData fromJson(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new EarningsData(
                root.has("symbol") ? root.get("symbol").asText() : null,
                root.has("quarter") ? root.get("quarter").asText() : null,
                root.has("revenue") ? toBigDecimal(root.get("revenue")) : null,
                root.has("netProfit") ? toBigDecimal(root.get("netProfit")) : null,
                root.has("eps") ? toBigDecimal(root.get("eps")) : null,
                root.has("ebitda") ? toBigDecimal(root.get("ebitda")) : null,
                root.has("guidance") ? root.get("guidance").asText() : null,
                LocalDate.now()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node.isBigDecimal()) return node.decimalValue();
        if (node.isDouble()) return BigDecimal.valueOf(node.doubleValue());
        if (node.isLong()) return BigDecimal.valueOf(node.longValue());
        if (node.isInt()) return BigDecimal.valueOf(node.intValue());
        if (node.isTextual()) return new BigDecimal(node.asText());
        return null;
    }
}