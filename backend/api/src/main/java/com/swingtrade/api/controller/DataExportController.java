package com.swingtrade.api.controller;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Bounded, paged OHLCV export for backup and external analysis. */
@RestController
@RequestMapping("/api/data")
public class DataExportController {

    private static final int PAGE_SIZE = 1_000;
    private final OhlcvCandleRepository repository;

    public DataExportController(OhlcvCandleRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/export")
    public ResponseEntity<?> export(
            @RequestParam(required = false, name = "symbol") List<String> requestedSymbols,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate,
            @RequestParam(defaultValue = "CSV") String format) {
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body("fromDate must not be after toDate");
        }
        String normalizedFormat = format.toUpperCase(Locale.ROOT);
        if (!normalizedFormat.equals("CSV") && !normalizedFormat.equals("JSON")) {
            return ResponseEntity.badRequest().body("format must be CSV or JSON");
        }
        List<String> symbols = requestedSymbols == null ? List.of() : requestedSymbols.stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim).map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        if (symbols.isEmpty()) {
            symbols = repository.findAllDistinctSymbols();
        }
        if (symbols.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        final List<String> exportSymbols = symbols;
        boolean json = normalizedFormat.equals("JSON");
        StreamingResponseBody body = output -> writeExport(output, exportSymbols, fromDate, toDate, json);
        String extension = json ? "json" : "csv";
        MediaType mediaType = json ? MediaType.APPLICATION_JSON : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("ohlcv-export." + extension).build().toString())
                .body(body);
    }

    private void writeExport(java.io.OutputStream output, List<String> symbols,
                             LocalDate fromDate, LocalDate toDate, boolean json) throws IOException {
        Writer writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(output,
                java.nio.charset.StandardCharsets.UTF_8));
        if (json) writer.write("[");
        else writer.write("symbol,date,open,high,low,close,volume\n");
        boolean first = true;
        for (int page = 0; ; page++) {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE);
            List<OhlcvCandleEntity> candles = repository.findBySymbolsAndDateRange(
                    symbols, fromDate, toDate, pageable);
            for (OhlcvCandleEntity candle : candles) {
                if (json) {
                    if (!first) writer.write(",");
                    writer.write("{\"symbol\":\""); writer.write(jsonEscape(candle.getSymbol()));
                    writer.write("\",\"date\":\""); writer.write(candle.getDate().toString());
                    writer.write("\",\"open\":"); writer.write(String.valueOf(candle.getOpenPrice()));
                    writer.write(",\"high\":"); writer.write(String.valueOf(candle.getHighPrice()));
                    writer.write(",\"low\":"); writer.write(String.valueOf(candle.getLowPrice()));
                    writer.write(",\"close\":"); writer.write(String.valueOf(candle.getClosePrice()));
                    writer.write(",\"volume\":"); writer.write(String.valueOf(candle.getVolume()));
                    writer.write("}");
                } else {
                    writer.write(csv(candle.getSymbol())); writer.write(","); writer.write(candle.getDate().toString());
                    writer.write(","); writer.write(csv(candle.getOpenPrice()));
                    writer.write(","); writer.write(csv(candle.getHighPrice()));
                    writer.write(","); writer.write(csv(candle.getLowPrice()));
                    writer.write(","); writer.write(csv(candle.getClosePrice()));
                    writer.write(","); writer.write(csv(candle.getVolume())); writer.write("\n");
                }
                first = false;
            }
            writer.flush();
            if (candles.size() < PAGE_SIZE) break;
        }
        if (json) writer.write("]");
        writer.flush();
    }

    private static String csv(Object value) {
        return value == null ? "" : "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
