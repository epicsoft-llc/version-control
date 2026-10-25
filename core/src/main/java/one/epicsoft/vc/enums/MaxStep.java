package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How far a single suggested step may go. MAJOR — no limit — is a legitimate and
 * frequent choice, not an exception.
 */
@Schema(enumAsRef = true, description = """
    How far a suggested step may go; the default is `MINOR`.

    - `PATCH`: the newest version of the running minor line.
    - `MINOR`: the newest version of the running major line.
    - `MAJOR`: no limit.
    - `NONE`: shows what is available, suggests nothing.

    A newer version past the limit is reported as `beyond`, a note that creates no task.
    """)
public enum MaxStep {

  PATCH,
  MINOR,
  MAJOR,

  /** Show what is available, suggest nothing. */
  NONE
}
