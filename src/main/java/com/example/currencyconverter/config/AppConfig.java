package com.example.currencyconverter.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads settings from environment variables first, then config/app.properties. */
public record AppConfig(String apiBaseUrl, int apiTimeoutSeconds, String dbUrl, String dbUser, String dbPassword) {
    public static AppConfig load() {
        Properties p = new Properties();
        Path local = Path.of("config", "app.properties");
        if (Files.exists(local)) {
            try (InputStream in = Files.newInputStream(local)) { p.load(in); }
            catch (IOException ex) { System.err.println("Could not read config/app.properties: " + ex.getMessage()); }
        }
        return new AppConfig(value("CURRENCY_API_BASE_URL", p, "api.base-url", "https://api.frankfurter.dev/v2"),
                integer("CURRENCY_API_TIMEOUT_SECONDS", p, "api.timeout-seconds", 12),
                value("CURRENCY_DB_URL", p, "db.url", ""),
                value("CURRENCY_DB_USER", p, "db.user", ""),
                value("CURRENCY_DB_PASSWORD", p, "db.password", ""));
    }
    private static String value(String env, Properties p, String key, String fallback) {
        String v = System.getenv(env); return v != null && !v.isBlank() ? v.trim() : p.getProperty(key, fallback).trim();
    }
    private static int integer(String env, Properties p, String key, int fallback) {
        try { return Integer.parseInt(value(env, p, key, String.valueOf(fallback))); }
        catch (NumberFormatException ex) { return fallback; }
    }
    public boolean hasDatabase() { return dbUrl != null && !dbUrl.isBlank() && dbUser != null && !dbUser.isBlank(); }
}
