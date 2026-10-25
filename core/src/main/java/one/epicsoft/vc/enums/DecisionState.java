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
    - `AUTO_UPDATE`: updates itself — an updater on the host installs every new version, so none of them is a task; \
    no newer version reopens it.
    - `BLOCKED`: derived from the dependency check, cannot be set by hand.
    """)
public enum DecisionState {

  OPEN,
  PLANNED,
  POSTPONED,
  SKIPPED,

  /** Stays where it is, whatever comes out; the reason is required, and nothing newer reopens it. */
  HELD,

  /** Updates itself, so no new version is a task; nothing newer reopens it. */
  AUTO_UPDATE,

  /** Derived from the dependency check, never set by hand. */
  BLOCKED;

  /** A decision about the component rather than about one version: no newer version reopens it. */
  public boolean isPermanent() {
    return HELD == this || AUTO_UPDATE == this;
  }
}
