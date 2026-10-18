package com.example.currencyconverter.ui;

import com.example.currencyconverter.config.AppConfig;
import com.example.currencyconverter.db.DatabaseRepository;
import com.example.currencyconverter.model.HistoryEntry;
import com.example.currencyconverter.model.RateQuote;
import com.example.currencyconverter.service.ExchangeRateService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Swing view and controller. Background workers keep network/database calls off the UI thread. */
public final class ConverterFrame extends JFrame {
    private final AppConfig config;
    private final DatabaseRepository database;
    private final ExchangeRateService rates;
    private final JComboBox<String> fromCurrency = new JComboBox<>(new String[]{"USD", "EUR", "GBP", "INR", "JPY", "CAD", "AUD", "CHF"});
    private final JComboBox<String> toCurrency = new JComboBox<>(new String[]{"EUR", "USD", "GBP", "INR", "JPY", "CAD", "AUD", "CHF"});
    private final JTextField amount = new JTextField("1.00", 12);
    private final JTextField manualRate = new JTextField("1.000000", 10);
    private final JRadioButton apiMode = new JRadioButton("Daily reference rate (online)", true);
    private final JRadioButton manualMode = new JRadioButton("Fixed/manual rate (offline)");
    private final JLabel result = new JLabel("Enter an amount and choose Convert.");
    private final JLabel rateInfo = new JLabel("Rate source and observation date will appear here.");
    private final JLabel status = new JLabel("Ready.");
    private final JButton convertButton = new JButton("Convert");
    private final JButton refreshButton = new JButton("Refresh rate");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"When", "Amount", "From", "Rate", "To", "Converted", "Source"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable historyTable = new JTable(tableModel);

    public ConverterFrame(AppConfig config) {
        super("Currency Converter");
        this.config = config;
        this.database = new DatabaseRepository(config);
        this.rates = new ExchangeRateService(config.apiBaseUrl(), config.apiTimeoutSeconds());
        buildWindow();
        loadSavedSettings();
        loadHistory();
        loadCurrencies();
    }
    private void buildWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(780, 620));
        setSize(930, 700);
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        setContentPane(root);

        JLabel title = new JLabel("Currency Converter");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Compare currencies with published reference data or a rate you enter yourself.");
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(title, BorderLayout.NORTH); heading.add(subtitle, BorderLayout.SOUTH);
        root.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 12));
        root.add(center, BorderLayout.CENTER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Conversion"));
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(6, 8, 6, 8); c.anchor = GridBagConstraints.WEST;
        addField(form, c, 0, 0, "Amount", amount);
        addField(form, c, 0, 1, "From currency", fromCurrency);
        JButton swap = new JButton("Swap currencies");
        c.gridx = 2; c.gridy = 1; c.gridwidth = 2; form.add(swap, c); c.gridwidth = 1;
        addField(form, c, 0, 2, "To currency", toCurrency);
        ButtonGroup group = new ButtonGroup(); group.add(apiMode); group.add(manualMode);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 4; form.add(apiMode, c);
        c.gridy = 4; form.add(manualMode, c); c.gridwidth = 1;
        addField(form, c, 0, 5, "Manual rate (1 source = ? target)", manualRate);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(convertButton); buttons.add(refreshButton);
        JButton clear = new JButton("Clear / reset"); buttons.add(clear);
        c.gridx = 0; c.gridy = 6; c.gridwidth = 4; form.add(buttons, c);
        center.add(form, BorderLayout.NORTH);

        JPanel answer = new JPanel(new BorderLayout(3, 7));
        answer.setBorder(BorderFactory.createTitledBorder("Result"));
        result.setFont(result.getFont().deriveFont(Font.BOLD, 20f));
        answer.add(result, BorderLayout.NORTH); answer.add(rateInfo, BorderLayout.CENTER);
        center.add(answer, BorderLayout.CENTER);
        JPanel history = new JPanel(new BorderLayout());
        history.setBorder(BorderFactory.createTitledBorder("Recent conversion history (stored in MySQL)"));
        historyTable.setFillsViewportHeight(true); historyTable.setAutoCreateRowSorter(true);
        history.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        history.setPreferredSize(new Dimension(700, 215));
        root.add(history, BorderLayout.SOUTH);
        root.add(status, BorderLayout.PAGE_END);

        apiMode.addActionListener(e -> updateRateControls()); manualMode.addActionListener(e -> updateRateControls());
        updateRateControls();
        swap.addActionListener(e -> { Object a = fromCurrency.getSelectedItem(); fromCurrency.setSelectedItem(toCurrency.getSelectedItem()); toCurrency.setSelectedItem(a); saveSelections(); });
        clear.addActionListener(e -> { amount.setText(""); manualRate.setText("1.000000"); result.setText("Enter an amount and choose Convert."); rateInfo.setText("Rate source and observation date will appear here."); status.setText("Form cleared."); });
        convertButton.addActionListener(e -> convert()); refreshButton.addActionListener(e -> refreshRate());
        fromCurrency.addActionListener(e -> saveSelections()); toCurrency.addActionListener(e -> saveSelections());
    }
    private void addField(JPanel panel, GridBagConstraints c, int x, int y, String label, java.awt.Component field) {
        c.gridx = x; c.gridy = y; c.gridwidth = 1; panel.add(new JLabel(label + ":"), c);
        c.gridx = x + 1; panel.add(field, c);
    }
    private void updateRateControls() {
        boolean manual = manualMode.isSelected(); manualRate.setEnabled(manual); refreshButton.setEnabled(!manual);
    }
    private String selected(JComboBox<String> combo) { Object v = combo.getSelectedItem(); return v == null ? "" : v.toString().trim().toUpperCase(); }
    private BigDecimal readAmount() {
        try {
            BigDecimal value = new BigDecimal(amount.getText().trim());
            if (value.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
            if (value.precision() > 20 || Math.max(0, value.scale()) > 6) throw new IllegalArgumentException("Use at most 20 significant digits and 6 decimal places.");
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid number, such as 125.50."); }
    }
    private void convert() {
        final String from = selected(fromCurrency), to = selected(toCurrency);
        final BigDecimal value;
        try { value = readAmount(); }
        catch (IllegalArgumentException ex) { showError(ex.getMessage()); return; }
        if (from.isBlank() || to.isBlank()) { showError("Choose both a source and target currency."); return; }
        convertButton.setEnabled(false); status.setText("Converting...");
        new SwingWorker<Conversion, Void>() {
            @Override protected Conversion doInBackground() throws Exception {
                RateQuote quote;
                if (manualMode.isSelected()) {
                    BigDecimal rate;
                    try { rate = new BigDecimal(manualRate.getText().trim()); }
                    catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid manual rate, for example 1.25."); }
                    if (rate.signum() <= 0 || rate.precision() > 24 || Math.max(0, rate.scale()) > 12) throw new IllegalArgumentException("Manual rate must be positive, with at most 12 decimal places.");
                    quote = new RateQuote(rate, null, "Manual fixed rate");
                } else quote = rates.getRate(from, to);
                BigDecimal converted = value.multiply(quote.rate()).setScale(2, RoundingMode.HALF_UP);
                return new Conversion(from, to, value, quote, converted);
            }
            @Override protected void done() {
                convertButton.setEnabled(true);
                try {
                    Conversion conversion = get();
                    result.setText(conversion.amount().toPlainString() + " " + conversion.from() + " = " + conversion.converted().toPlainString() + " " + conversion.to());
                    rateInfo.setText("1 " + conversion.from() + " = " + conversion.quote().rate().stripTrailingZeros().toPlainString() + " " + conversion.to() + "  |  " + conversion.quote().source() + (conversion.quote().rateDate() == null ? "" : "  |  Observation date: " + conversion.quote().rateDate()));
                    status.setText("Conversion complete.");
                    saveConversion(conversion);
                    saveSelections();
                } catch (Exception ex) { showError(userMessage(ex)); }
            }
        }.execute();
    }
    private void refreshRate() {
        String from = selected(fromCurrency), to = selected(toCurrency);
        if (from.isBlank() || to.isBlank()) { showError("Choose both currencies first."); return; }
        refreshButton.setEnabled(false); status.setText("Getting the latest published reference rate...");
        new SwingWorker<RateQuote, Void>() {
            @Override protected RateQuote doInBackground() throws Exception { return rates.getRate(from, to); }
            @Override protected void done() {
                refreshButton.setEnabled(!manualMode.isSelected());
                try {
                    RateQuote quote = get();
                    rateInfo.setText("1 " + from + " = " + quote.rate().stripTrailingZeros().toPlainString() + " " + to + "  |  " + quote.source() + (quote.rateDate() == null ? "" : "  |  Observation date: " + quote.rateDate()));
                    status.setText("Rate refreshed. This is daily reference data, not a live market quote.");
                } catch (Exception ex) { status.setText("Rate refresh failed."); showError(userMessage(ex)); }
            }
        }.execute();
    }
    private void saveConversion(Conversion item) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                database.saveConversion(item.from(), item.to(), item.amount(), item.quote().rate(), item.converted(), item.quote().source(), item.quote().rateDate());
                return null;
            }
            @Override protected void done() {
                try { get(); status.setText("Conversion saved to MySQL history."); loadHistory(); }
                catch (Exception ex) { status.setText("Conversion worked, but it could not be saved to MySQL."); JOptionPane.showMessageDialog(ConverterFrame.this, "The conversion succeeded, but history could not be saved. Check MySQL and config/app.properties.\n\n" + userMessage(ex), "Database unavailable", JOptionPane.WARNING_MESSAGE); }
            }
        }.execute();
    }
    private void loadHistory() {
        if (!database.isConfigured()) { status.setText("MySQL is not configured. Conversions can still be done; history will not persist."); return; }
        new SwingWorker<List<HistoryEntry>, Void>() {
            @Override protected List<HistoryEntry> doInBackground() throws Exception { return database.loadHistory(100); }
            @Override protected void done() {
                try {
                    List<HistoryEntry> rows = get(); tableModel.setRowCount(0);
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    for (HistoryEntry r : rows) tableModel.addRow(new Object[]{r.convertedAt().format(fmt), r.amount().toPlainString(), r.from(), r.rate().stripTrailingZeros().toPlainString(), r.to(), r.converted().toPlainString(), r.source()});
                } catch (Exception ex) { status.setText("Could not load history from MySQL; conversions remain available."); }
            }
        }.execute();
    }
    private void saveSelections() {
        if (!database.isConfigured() || fromCurrency.getSelectedItem() == null || toCurrency.getSelectedItem() == null) return;
        String from = selected(fromCurrency), to = selected(toCurrency);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { database.saveSetting("from_currency", from); database.saveSetting("to_currency", to); return null; }
            @Override protected void done() { try { get(); } catch (Exception ex) { /* A selection remains available for this run even if MySQL is down. */ } }
        }.execute();
    }
    private void loadSavedSettings() {
        if (!database.isConfigured()) return;
        new SwingWorker<String[], Void>() {
            @Override protected String[] doInBackground() throws Exception {
                return new String[]{database.getSetting("from_currency").orElse("USD"), database.getSetting("to_currency").orElse("EUR")};
            }
            @Override protected void done() {
                try { String[] values = get(); fromCurrency.setSelectedItem(values[0]); toCurrency.setSelectedItem(values[1]); }
                catch (Exception ignored) { /* Missing settings table or offline DB: use defaults. */ }
            }
        }.execute();
    }
    private void loadCurrencies() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() throws Exception { return rates.getCurrencies(); }
            @Override protected void done() {
                try {
                    String from = selected(fromCurrency), to = selected(toCurrency);
                    List<String> codes = get(); fromCurrency.removeAllItems(); toCurrency.removeAllItems();
                    for (String code : codes) { fromCurrency.addItem(code); toCurrency.addItem(code); }
                    fromCurrency.setSelectedItem(codes.contains(from) ? from : "USD");
                    toCurrency.setSelectedItem(codes.contains(to) ? to : "EUR");
                    status.setText("Currency list loaded. Rates are published daily reference observations.");
                } catch (Exception ex) { status.setText("Could not load currency list. Default list is available; check internet/API settings."); }
            }
        }.execute();
    }
    private void showError(String message) { JOptionPane.showMessageDialog(this, message, "Please check your input or connection", JOptionPane.ERROR_MESSAGE); }
    private String userMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        if (root instanceof IllegalArgumentException) return root.getMessage();
        String message = root.getMessage();
        if (message == null || message.isBlank()) return "The request could not be completed. Check your internet/API or database settings.";
        if (message.contains("ConnectException") || message.contains("UnknownHostException")) return "Could not reach the exchange-rate service. Check your internet connection, or select Fixed/manual rate for offline use.";
        return message;
    }
    private record Conversion(String from, String to, BigDecimal amount, RateQuote quote, BigDecimal converted) { }
}



