package org.c2w.infra;

/**
 * The GitHub repository of Cow2Win - shared by the program update check ({@link UpdateChecker})
 * and the catalog download ({@link GitHubCatalogRemote}).
 */
public final class GitHubProject {

    /** GitHub org/repo - see this project's own "origin" git remote. */
    public static final String OWNER = "Cow2Win";
    public static final String REPO = "Cow2Win";

    /** The branch the catalog is published on (the repository's default branch): a push there is a release of the catalog. */
    public static final String CATALOG_BRANCH = "master";

    /** The project's page on GitHub - shown in "Help" &gt; "About Cow2Win". */
    public static final String PROJECT_PAGE_URL = "https://github.com/" + OWNER + "/" + REPO;

    /** The project's license file (MIT) on GitHub. */
    public static final String LICENSE_URL = PROJECT_PAGE_URL + "/blob/" + CATALOG_BRANCH + "/LICENSE";

    /** The human-facing releases page. */
    public static final String RELEASES_PAGE_URL = PROJECT_PAGE_URL + "/releases";

    /** The REST endpoint of the latest release. */
    public static final String LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

    /** Base URL of the published catalog's raw files, ending with {@code /}. */
    public static final String CATALOG_RAW_URL =
            "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + CATALOG_BRANCH + "/catalog/";

    private GitHubProject() {
    }
}
