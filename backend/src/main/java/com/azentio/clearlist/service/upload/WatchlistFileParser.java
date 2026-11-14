package com.azentio.clearlist.service.upload;

import com.azentio.clearlist.dto.ErrorResponse.Detail;
import com.azentio.clearlist.exception.UnsupportedUploadException;
import com.azentio.clearlist.exception.WatchlistValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded CSV or JSON file into raw {@link WatchlistRow}s. Only structural problems are reported
 * here (unreadable file, missing columns); field-level checks are done by {@link WatchlistRowValidator}.
 *
 * <p>CSV columns: entryId,primaryName,aliases,dob,nationality,listType (aliases separated by '|').
 * JSON: an array of objects with the same field names; aliases may be an array or a '|' separated string.
 */
@Component
public class WatchlistFileParser {

    static final List<String> COLUMNS = List.of("entryId", "primaryName", "aliases", "dob", "nationality", "listType");
    private static final Set<String> REQUIRED_COLUMNS = Set.of("entryId", "primaryName", "listType");
    private static final String ALIAS_SEPARATOR = "\\|";

    private final ObjectMapper objectMapper;

    public WatchlistFileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<WatchlistRow> parse(String filename, String contentType, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) { // Excel-style UTF-8 BOM
            text = text.substring(1);
        }
        return switch (detectFormat(filename, contentType)) {
            case CSV -> parseCsv(text);
            case JSON -> parseJson(text);
        };
    }

    private Format detectFormat(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv") || type.startsWith("text/csv")) {
            return Format.CSV;
        }
        if (name.endsWith(".json") || type.startsWith("application/json")) {
            return Format.JSON;
        }
        throw new UnsupportedUploadException("Watchlist must be a .csv or .json file");
    }

    private List<WatchlistRow> parseCsv(String text) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            List<String> missing = REQUIRED_COLUMNS.stream()
                    .filter(column -> !parser.getHeaderMap().containsKey(column)).sorted().toList();
            if (!missing.isEmpty()) {
                throw new WatchlistValidationException("CSV header is missing required columns",
                        List.of(new Detail("header", "missing columns " + missing + "; expected " + COLUMNS)));
            }
            List<WatchlistRow> rows = new ArrayList<>();
            int rowNumber = 0;
            for (CSVRecord record : parser) {
                rowNumber++;
                if (!record.isConsistent()) {
                    throw new WatchlistValidationException("CSV row has the wrong number of columns",
                            List.of(new Detail("row " + rowNumber, "expected " + parser.getHeaderNames().size()
                                    + " columns but found " + record.size())));
                }
                rows.add(new WatchlistRow(rowNumber, csvValue(record, "entryId"), csvValue(record, "primaryName"),
                        splitAliases(csvValue(record, "aliases")), csvValue(record, "dob"),
                        csvValue(record, "nationality"), csvValue(record, "listType")));
            }
            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException | java.io.UncheckedIOException e) {
            throw new WatchlistValidationException("CSV file could not be parsed",
                    List.of(new Detail("file", e.getMessage())));
        }
    }

    private static String csvValue(CSVRecord record, String column) {
        return record.isMapped(column) ? blankToNull(record.get(column)) : null;
    }

    private List<WatchlistRow> parseJson(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new WatchlistValidationException("JSON file could not be parsed",
                    List.of(new Detail("file", e.getOriginalMessage())));
        }
        if (root == null || !root.isArray()) {
            throw new WatchlistValidationException("JSON watchlist must be an array of entries",
                    List.of(new Detail("file", "expected a top-level JSON array")));
        }
        List<WatchlistRow> rows = new ArrayList<>();
        int rowNumber = 0;
        for (JsonNode node : root) {
            rowNumber++;
            if (!node.isObject()) {
                throw new WatchlistValidationException("JSON watchlist entries must be objects",
                        List.of(new Detail("row " + rowNumber, "expected a JSON object")));
            }
            rows.add(new WatchlistRow(rowNumber, jsonText(node, "entryId"), jsonText(node, "primaryName"),
                    jsonAliases(node, rowNumber), jsonText(node, "dob"), jsonText(node, "nationality"),
                    jsonText(node, "listType")));
        }
        return rows;
    }

    private static String jsonText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : blankToNull(value.asText());
    }

    private static List<String> jsonAliases(JsonNode node, int rowNumber) {
        JsonNode value = node.get("aliases");
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.isTextual()) {
            return splitAliases(value.asText());
        }
        if (!value.isArray()) {
            throw new WatchlistValidationException("JSON aliases must be an array of strings",
                    List.of(new Detail("row " + rowNumber + ": aliases", "expected an array of strings")));
        }
        List<String> aliases = new ArrayList<>();
        value.forEach(alias -> {
            String text = blankToNull(alias.asText());
            if (text != null) {
                aliases.add(text);
            }
        });
        return aliases;
    }

    private static List<String> splitAliases(String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split(ALIAS_SEPARATOR)).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private enum Format { CSV, JSON }
}
