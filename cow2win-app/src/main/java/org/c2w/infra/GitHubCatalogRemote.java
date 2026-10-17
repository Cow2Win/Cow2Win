package org.c2w.infra;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The catalog published in the repository's {@code catalog/} folder on the {@link
 * GitHubProject#CATALOG_BRANCH} branch, fetched as raw files. GitHub serves raw files with a cache
 * of a few minutes, so a fresh push is not visible right away - no workaround needed.
 */
public final class GitHubCatalogRemote implements CatalogRemote {

    /** Timeout of every single request. */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final HttpClient client;

    public GitHubCatalogRemote() {
        this(GitHubProject.CATALOG_RAW_URL);
    }

    /** For another base URL (ending with {@code /}). */
    GitHubCatalogRemote(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public byte[] fetch(String path) throws IOException {
        URI uri = URI.create(baseUrl + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The download of " + path + " was interrupted.", e);
        } catch (IOException e) {
            throw new IOException("GitHub could not be reached (" + describe(e) + ").", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub answered HTTP " + response.statusCode() + " for " + path + ".");
        }
        return response.body();
    }

    private static String describe(IOException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
