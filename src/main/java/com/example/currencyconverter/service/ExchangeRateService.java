package com.example.currencyconverter.service;

import com.example.currencyconverter.model.RateQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fetches published reference rates. It does not provide tick-by-tick market prices. */
public final class ExchangeRateService {
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper();
    public ExchangeRateService(String baseUrl, int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    }
    public RateQuote getRate(String from, String to) throws IOException, InterruptedException {
        if (from.equalsIgnoreCase(to)) return new RateQuote(BigDecimal.ONE, LocalDate.now(), "Same currency");
        String url = baseUrl + "/rate/" + from.toLowerCase() + "/" + to.toLowerCase();
        JsonNode root = getJson(url);
        JsonNode rate = root.get("rate");
        if (rate == null || !rate.isNumber()) throw new IOException("The API response did not contain a usable rate.");
        LocalDate date = root.hasNonNull("date") ? LocalDate.parse(root.get("date").asText()) : null;
        return new RateQuote(rate.decimalValue(), date, "Frankfurter daily reference rate");
    }
    public List<String> getCurrencies() throws IOException, InterruptedException {
        JsonNode root = getJson(baseUrl + "/currencies");
        List<String> codes = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                JsonNode code = item.get("iso_code");
                if (code == null) code = item.get("currency");
                if (code != null && code.isTextual()) codes.add(code.asText().toUpperCase());
            }
        } else if (root.isObject()) {
            root.fieldNames().forEachRemaining(codes::add);
        }
        if (codes.isEmpty()) throw new IOException("The API returned no supported currencies.");
        codes.sort(String::compareTo);
        return codes;
    }
    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "Exchange-rate service returned HTTP " + response.statusCode();
            try { message += ": " + json.readTree(response.body()).path("message").asText(""); }
            catch (Exception ignored) { }
            throw new IOException(message);
        }
        return json.readTree(response.body());
    }
}

