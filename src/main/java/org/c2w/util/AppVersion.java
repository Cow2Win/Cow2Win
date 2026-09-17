package org.c2w.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads this build's own version out of {@code app-version.properties} - a
 * classpath resource generated at build time by Maven resource filtering
 * (see {@code pom.xml}'s {@code <resources>} section) from the
 * {@code app.version} property, the same one {@code jpackage} uses as the
 * packaged app's version. Exists so {@link UpdateChecker} (Cow2Win todos
 * 1.1) can compare "what's installed" against "what's the latest release on
 * GitHub" without hardcoding the version a second time in Java source.
 *
 * <p>Read once and cached, same convention as {@link CatalogVersion}. Falls
 * back to {@value #UNKNOWN} - never the literal, unresolved
 * {@code "${app.version}"} - if the file is missing or filtering did not run
 * (e.g. resources compiled some other way than through Maven's
 * process-resources phase, which can happen for a plain IDE run/compile
 * depending on how the module was imported), so a broken version string can
 * never masquerade as a real one to compare against. {@link #UNKNOWN}
 * deliberately doesn't match {@link UpdateChecker}'s version pattern, so
 * that case just makes the update check silently report "could not compare"
 * instead of a wrong result.
 */
public final class AppVersion {

    private static final String PROPERTIES_PATH = "/app-version.properties";
    private static final String PROPERTY_KEY = "version";
    private static final String UNKNOWN = "0.0.0-dev";

    private static volatile String version;

    private AppVersion() {
    }

    /** This build's version, e.g. {@code "1.0.0"} - or {@value #UNKNOWN} if it could not be determined (see class Javadoc). */
    public static String current() {
        ensureLoaded();
        return version;
    }

    private static void ensureLoaded() {
        if (version == null) {
            synchronized (AppVersion.class) {
                if (version == null) {
                    version = load();
                }
            }
        }
    }

    private static String load() {
        try (InputStream in = AppVersion.class.getResourceAsStream(PROPERTIES_PATH)) {
            if (in == null) {
                Logger.log("app-version.properties not found on classpath - falling back to " + UNKNOWN);
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String raw = properties.getProperty(PROPERTY_KEY, "").trim();
            if (raw.isEmpty() || raw.startsWith("${")) {
                Logger.log("app-version.properties has no resolved version (\"" + raw + "\") - falling back to " + UNKNOWN);
                return UNKNOWN;
            }
            return raw;
        } catch (IOException e) {
            Logger.logException("Could not read " + PROPERTIES_PATH, e);
            return UNKNOWN;
        }
    }
}
