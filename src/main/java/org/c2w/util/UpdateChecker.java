package org.c2w.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub for a newer Cow2Win release than the one currently
 * installed (Cow2Win todos 1.1) - used both for a silent check once at
 * startup ({@code Cow2Frame}, only ever surfaces a dialog when an update
 * was actually found) and for the explicit "Check for updates" menu item
 * ({@code Cow2Frame}, always reports back - including "already up to
 * date"/a failed check).
 *
 * <p>Talks to GitHub's public REST API
 * ({@code GET /repos/{owner}/{repo}/releases/latest}), which only ever
 * returns the latest *published, non-draft, non-prerelease* release -
 * exactly the "is there a newer version I should install" question this
 * needs answered, with no extra filtering needed here. For this to find
 * anything, the release's git tag needs to be a dotted major.minor.patch
 * version, optionally "v"-prefixed (e.g. {@code v1.2.0} or {@code 1.2.0}) -
 * see {@link #parseVersion} - which is what {@code
 * .github/workflows/release.yml} tags its releases with.
 *
 * <p>Uses the JDK's built-in {@link HttpClient} rather than pulling in a new
 * dependency for one small GET request. Never throws out of this class:
 * every failure (no network, a non-200 response - including a 404 when the
 * repository has no release yet -, a malformed response body, or a tag that
 * doesn't parse as a version) comes back as {@link
 * UpdateCheckResult.Status#CHECK_FAILED} and is logged via {@link Logger},
 * exactly like a network-dependent feature should - see also {@link
 * CatalogVersion}/{@link BackupService} for the same "never break the app"
 * convention elsewhere in this project.
 */
public final class UpdateChecker {

    /** GitHub org/repo to check - see this project's own "origin" git remote. */
    private static final String GITHUB_OWNER = "Cow2Win";
    private static final String GITHUB_REPO = "Cow2Win";
    private static final String LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    /** Fallback link (e.g. for a failed check, or if the API response carries no usable URL) - the human-facing releases page, not the API endpoint above. */
    private static final String RELEASES_PAGE_URL =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)$");

    private UpdateChecker() {
    }

    /** Outcome of one check - {@code latestVersion}/{@code releaseUrl} are only meaningful for {@link Status#UPDATE_AVAILABLE}. */
    public record UpdateCheckResult(Status status, String currentVersion, String latestVersion, String releaseUrl) {
        public enum Status {UPDATE_AVAILABLE, UP_TO_DATE, CHECK_FAILED}
    }

    /**
     * Runs the check on a background thread (never on the caller's thread,
     * so this is safe to call directly from the Swing event thread) and
     * calls {@code onResult} back on the Swing event thread once done.
     */
    public static void checkAsync(Consumer<UpdateCheckResult> onResult) {
        CompletableFuture.supplyAsync(UpdateChecker::checkNow)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> onResult.accept(result)));
    }

    private static UpdateCheckResult checkNow() {
        String current = AppVersion.current();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API_URL))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                Logger.log("Update check: GitHub returned HTTP " + response.statusCode()
                        + " for " + LATEST_RELEASE_API_URL);
                return failed(current);
            }

            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            String tagName = JsonSupport.getString(body, "tag_name", "");
            String htmlUrl = JsonSupport.getString(body, "html_url", RELEASES_PAGE_URL);

            Optional<int[]> latestVersion = parseVersion(tagName);
            Optional<int[]> currentVersion = parseVersion(current);
            if (latestVersion.isEmpty() || currentVersion.isEmpty()) {
                Logger.log("Update check: could not compare versions (installed=\"" + current
                        + "\", latest release tag=\"" + tagName + "\")");
                return failed(current);
            }

            if (compare(latestVersion.get(), currentVersion.get()) > 0) {
                return new UpdateCheckResult(UpdateCheckResult.Status.UPDATE_AVAILABLE,
                        current, stripLeadingV(tagName), htmlUrl);
            }
            return new UpdateCheckResult(UpdateCheckResult.Status.UP_TO_DATE, current, current, htmlUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.log("Update check interrupted: " + e.getMessage());
            return failed(current);
        } catch (IOException | RuntimeException e) {
            Logger.log("Update check failed: " + e.getMessage());
            return failed(current);
        }
    }

    private static UpdateCheckResult failed(String current) {
        return new UpdateCheckResult(UpdateCheckResult.Status.CHECK_FAILED, current, null, RELEASES_PAGE_URL);
    }

    /** Strips a leading "v"/"V" (e.g. "v1.2.0" -> "1.2.0") - GitHub tag convention, purely cosmetic for display. */
    private static String stripLeadingV(String tag) {
        return (!tag.isEmpty() && (tag.charAt(0) == 'v' || tag.charAt(0) == 'V')) ? tag.substring(1) : tag;
    }

    /**
     * Parses a dotted major.minor.patch version, optionally "v"-prefixed,
     * into {@code [major, minor, patch]} - empty if it doesn't match this
     * exact shape (e.g. a pre-release/build-metadata suffix like
     * "1.2.0-beta" or "1.2.0+42", or {@link AppVersion}'s dev fallback).
     * Package-private for {@code UpdateCheckerVersionTest}.
     */
    static Optional<int[]> parseVersion(String version) {
        if (version == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        });
    }

    /** Lexicographic major/minor/patch comparison - positive if {@code a} is newer than {@code b}. Package-private for {@code UpdateCheckerVersionTest}. */
    static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }
}
