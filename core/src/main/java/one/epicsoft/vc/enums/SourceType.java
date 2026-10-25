package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where the update check looks for newer versions. */
@Schema(enumAsRef = true, description = """
    Where the update check looks for newer versions, and what `sourceRef` names there.

    - `DOCKER_HUB`: image tags on Docker Hub; `sourceRef` is the repository with its namespace, such as `library/postgres`.
    - `GITHUB_RELEASE`: releases of a GitHub repository, its tags where it has none; `sourceRef` is `owner/name`.
    - `APT_REPOSITORY`: the package index of an APT repository; `sourceRef` is its base URL, together with `aptSuite`, \
    `aptComponent` and `aptArchitecture`.
    - `GHCR`, `GENERIC_REGISTRY`: not supported yet — the check reports an error rather than a version.
    - `MANUAL`: no machine readable source; maintained by hand and never an error.
    """)
public enum SourceType {

  DOCKER_HUB,
  GHCR,
  GENERIC_REGISTRY,
  GITHUB_RELEASE,
  APT_REPOSITORY,

  /**
   * No machine readable source. The component is maintained by hand and never
   * produces an error state — this case is guaranteed to occur.
   */
  MANUAL
}
