package com.bugreplay.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * ConfigManager loads runtime settings and database credentials safely:
 * 1. Checks for a local 'config.properties' file (which is git-ignored).
 * 2. Checks environment variables (e.g. BUGREPLAY_DB_URL, BUGREPLAY_DB_USER, BUGREPLAY_DB_PASS).
 * 3. Falls back to safe offline defaults.
 *
 * Real credentials are never hardcoded in source files.
 */
public class ConfigManager {

    public static final String DEFAULT_CONFIG_FILE = "config.properties";
    public static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/bugreplay?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String DEFAULT_DB_USER = "root";

    private final Properties properties = new Properties();
    private final boolean configFilePresent;

    public ConfigManager() {
        this(new File(DEFAULT_CONFIG_FILE));
    }

    public ConfigManager(File configFile) {
        boolean present = false;
        if (configFile != null && configFile.exists() && configFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                present = true;
            } catch (IOException e) {
                System.err.println("[ConfigManager] Could not load " + configFile.getName() + ": " + e.getMessage());
            }
        }
        this.configFilePresent = present;
    }

    public boolean isConfigFilePresent() {
        return configFilePresent;
    }

    public boolean isDbEnabled() {
        String env = System.getenv("BUGREPLAY_DB_ENABLED");
        if (env != null) {
            return "true".equalsIgnoreCase(env.trim());
        }
        String prop = properties.getProperty("db.enabled");
        if (prop != null) {
            return "true".equalsIgnoreCase(prop.trim());
        }
        // If config file is present, default to true; otherwise default to false to avoid unwanted warnings
        return configFilePresent;
    }

    public String getDbUrl() {
        String env = System.getenv("BUGREPLAY_DB_URL");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return properties.getProperty("db.url", DEFAULT_DB_URL).trim();
    }

    public String getDbUsername() {
        String env = System.getenv("BUGREPLAY_DB_USER");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return properties.getProperty("db.username", DEFAULT_DB_USER).trim();
    }

    public String getDbPassword() {
        String env = System.getenv("BUGREPLAY_DB_PASS");
        if (env != null) {
            return env;
        }
        return properties.getProperty("db.password", "");
    }
}
