package com.example.currencyconverter.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record HistoryEntry(long id, String from, String to, BigDecimal amount, BigDecimal rate,
                           BigDecimal converted, String source, LocalDateTime convertedAt) { }
