package com.example.currencyconverter;

import com.example.currencyconverter.config.AppConfig;
import com.example.currencyconverter.ui.ConverterFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { /* Swing's default theme remains usable. */ }
            new ConverterFrame(config).setVisible(true);
        });
    }
}
