package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of the last check. A failure is ERROR and never silently "current":
 * a broken rule must not look like everything being in order.
 */
@Schema(enumAsRef = true, description = """
    Outcome of the last check.

    - `OK`: the source answered.
    - `ERROR`: the check failed; the result of the last good one is kept, and `errorMessage` says why.
    - `NOT_APPLICABLE`: there is nothing to ask — not a failure.
    """)
public enum CheckState {

  OK,
  ERROR,

  /** No machine readable source — not a failure. */
  NOT_APPLICABLE
}
