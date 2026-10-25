package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** What the user decided about an available update. */
@Schema(enumAsRef = true, description = """
    What was decided about an available update. A component has one decision; it covers the version it was made about, \
    and a newer version reopens it.

    - `OPEN`: nothing decided — the update is on the task list.
    - `PLANNED`: will be done; off the task list.
    - `POSTPONED`: off the task list until `snoozeUntil`, then back as `SNOOZE_EXPIRED`.
    - `SKIPPED`: will not be done; off the task list.
    - `HELD`: stays on the running version for good, for the reason in `note`; no newer version reopens it.
    - `BLOCKED`: derived from the dependency check, cannot be set by hand.
    """)
public enum DecisionState {

  OPEN,
  PLANNED,
  POSTPONED,
  SKIPPED,

  /** Stays where it is, whatever comes out; the reason is required, and nothing newer reopens it. */
  HELD,

  /** Derived from the dependency check, never set by hand. */
  BLOCKED
}
