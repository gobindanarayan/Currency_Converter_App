package com.example.currencyconverter.db;

import com.example.currencyconverter.config.AppConfig;
import com.example.currencyconverter.model.HistoryEntry;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small JDBC repository: prepared statements keep values separate from SQL text. */
public final class DatabaseRepository {
    private final AppConfig config;
    public DatabaseRepository(AppConfig config) { this.config = config; }
    public boolean isConfigured() { return config.hasDatabase(); }
    public Connection connect() throws SQLException {
        if (!config.hasDatabase()) throw new SQLException("Database settings are missing. Copy config/app.properties.example to config/app.properties and configure db.url/db.user.");
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
    }
    public void saveConversion(String from, String to, BigDecimal amount, BigDecimal rate,
                               BigDecimal converted, String source, LocalDate rateDate) throws SQLException {
        String sql = "INSERT INTO conversion_history(from_currency,to_currency,amount,applied_rate,converted_amount,rate_source,rate_date) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from); ps.setString(2, to); ps.setBigDecimal(3, amount); ps.setBigDecimal(4, rate);
            ps.setBigDecimal(5, converted); ps.setString(6, source);
            if (rateDate == null) ps.setNull(7, Types.DATE); else ps.setDate(7, Date.valueOf(rateDate));
            ps.executeUpdate();
        }
    }
    public List<HistoryEntry> loadHistory(int limit) throws SQLException {
        List<HistoryEntry> rows = new ArrayList<>();
        String sql = "SELECT id,from_currency,to_currency,amount,applied_rate,converted_amount,rate_source,converted_at FROM conversion_history ORDER BY converted_at DESC,id DESC LIMIT ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new HistoryEntry(rs.getLong("id"), rs.getString("from_currency"), rs.getString("to_currency"),
                        rs.getBigDecimal("amount"), rs.getBigDecimal("applied_rate"), rs.getBigDecimal("converted_amount"),
                        rs.getString("rate_source"), rs.getTimestamp("converted_at").toLocalDateTime()));
            }
        }
        return rows;
    }
    public Optional<String> getSetting(String key) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT setting_value FROM app_settings WHERE setting_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
        }
    }
    public void saveSetting(String key, String value) throws SQLException {
        String sql = "INSERT INTO app_settings(setting_key,setting_value) VALUES(?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        }
    }
}
