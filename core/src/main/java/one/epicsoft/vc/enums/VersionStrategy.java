package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** How the newest of the matching candidates is determined. */
@Schema(enumAsRef = true, description = """
    How the newest of the matching candidates is chosen; the default is `SEMVER`.

    - `SEMVER`: semantic version ordering.
    - `LATEST_BY_DATE`: newest by publication date, for sources that carry timestamps.
    - `DIGEST`: the manifest digest instead of the tag — the only way to notice a moving tag such as `latest` changing.
    - `DEB_VERSION`: Debian version ordering, epoch first; for APT repositories, which carry no dates.
    """)
public enum VersionStrategy {

  /** Newest by publication date, for sources that carry timestamps. */
  LATEST_BY_DATE,

  /** Semantic version ordering, for sources that do not. */
  SEMVER,

  /** Manifest digest instead of the tag — the only way to notice a moving tag changing. */
  DIGEST,

  /** Debian version ordering: epoch first, and a tilde sorts before an empty remainder. */
  DEB_VERSION
}
