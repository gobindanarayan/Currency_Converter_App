package com.example.currencyconverter.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RateQuote(BigDecimal rate, LocalDate rateDate, String source) { }
