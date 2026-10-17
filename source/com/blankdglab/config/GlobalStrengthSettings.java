package com.blankdglab.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Output multipliers for the two device channels. Values are limited to 0..1. */
public final class GlobalStrengthSettings {
    private static final String OVERRIDE_PATH_PROPERTY = "blankdglab.globalStrength.configPath";
    private static final Path FILE = configPath();
    private static volatile double multiplierA = 1.0;
    private static volatile double multiplierB = 1.0;

    static {
        load();
    }

    private GlobalStrengthSettings() {}

    private static Path configPath() {
        String override = System.getProperty(OVERRIDE_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return FabricLoader.getInstance().getConfigDir().resolve("blankdglab-global-strength.properties");
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            values.load(input);
            multiplierA = readFactor(values.getProperty("multiplierA"));
            multiplierB = readFactor(values.getProperty("multiplierB"));
        } catch (IOException ex) {
            System.err.println("Blank DG-LAB: could not read global strength settings: " + FILE + " (" + ex.getMessage() + ")");
        }
    }

    private static double readFactor(String value) {
        if (value == null) {
            return 1.0;
        }
        try {
            double parsed = Double.parseDouble(value);
            return valid(parsed) ? parsed : 1.0;
        } catch (NumberFormatException ex) {
            return 1.0;
        }
    }

    private static boolean valid(double factor) {
        return Double.isFinite(factor) && factor >= 0.0 && factor <= 1.0;
    }

    public static double multiplierA() {
        return multiplierA;
    }

    public static double multiplierB() {
        return multiplierB;
    }

    public static synchronized void save(double a, double b) throws IOException {
        if (!valid(a) || !valid(b)) {
            throw new IllegalArgumentException("Multipliers must be between 0 and 1");
        }
        Files.createDirectories(FILE.getParent());
        Properties values = new Properties();
        values.setProperty("multiplierA", Double.toString(a));
        values.setProperty("multiplierB", Double.toString(b));
        Path temporary = Files.createTempFile(FILE.getParent(), "blankdglab-global-strength-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "Blank DG-LAB global output strength multipliers");
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        multiplierA = a;
        multiplierB = b;
    }

    public static int scale(int channel, int rawStrength) {
        double multiplier = channel == 1 ? multiplierA : multiplierB;
        return Math.max(0, Math.min(200, (int) Math.round(rawStrength * multiplier)));
    }

    /** Maximum raw strength that may become an allowed output after scaling. */
    public static int rawLimit(int channel, int outputLimit) {
        if (outputLimit <= 0) {
            return 0;
        }
        double multiplier = channel == 1 ? multiplierA : multiplierB;
        if (multiplier == 0.0) {
            return 200;
        }
        return Math.min(200, (int) Math.ceil(outputLimit / multiplier));
    }
}
