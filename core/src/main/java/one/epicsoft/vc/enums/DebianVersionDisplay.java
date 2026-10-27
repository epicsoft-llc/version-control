package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** How a Debian package version is written for a reader. */
@Schema(enumAsRef = true, description = """
    How a Debian package version is written for a reader; the default is `SHORT`. Only the display — stored, compared
    and copied versions are always complete.

    - `SHORT`: upstream version and package revision, `29.9.2-1` out of `5:29.9.2-1~ubuntu.24.04~noble`.
    - `FULL`: the version as the package reports it, with epoch and distribution.
    """)
public enum DebianVersionDisplay {

  /** Upstream version and package revision, without epoch and distribution. */
  SHORT,

  /** The version as the package reports it. */
  FULL
}
