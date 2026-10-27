package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The sections of the task list, in the order they are shown.
 *
 * <p>The order is the point: what is out of support comes before what is merely
 * behind, and a check that has been failing for days comes before a cosmetic
 * warning. The list answers "what has to be done" rather than leaving that to be
 * worked out from a table.
 */
// No quotes and no backslashes in the description: verifyMessageKeys reads this file to expand taskSection keys.
@Schema(enumAsRef = true, description = """
    The sections of the task list, in the order they are shown.

    - `EOL_EXCEEDED`: out of support, or past the escalation threshold.
    - `VULNERABLE`: what an environment runs has a known vulnerability that is exploited or reaches the score threshold.
    - `DEPENDENCY_VIOLATED`: a dependency that is violated right now.
    - `ROLLOUT_OVERDUE`: a rollout planned for one environment, and its day has passed.
    - `UPDATE_OPEN`: a newer version is available and nothing is decided.
    - `SNOOZE_EXPIRED`: postponed, and the date it was postponed to has passed.
    - `BLOCKED`: blocked by another component; the entry names it.
    - `CHECK_ERROR`: the check has been failing longer than the grace period.
    - `MOVING_TAG_IN_PRODUCTION`: a tag that moves, running in a production environment.
    - `EOL_WARNING`: support ends within the warning threshold.
    - `ROLLOUT_PLANNED`: a rollout planned for one environment, still ahead; for information.
    """)
public enum TaskSection {

  /** Out of support, or below the escalation threshold. */
  EOL_EXCEEDED,

  /** A known vulnerability in what runs, exploited or at the score threshold. */
  VULNERABLE,

  /** A dependency that is violated right now. */
  DEPENDENCY_VIOLATED,

  /** A planned rollout whose day has passed without the version being recorded. */
  ROLLOUT_OVERDUE,

  /** A newer version is available and nobody has decided anything. */
  UPDATE_OPEN,

  /** Postponed, and the date it was postponed to has passed. */
  SNOOZE_EXPIRED,

  /** Blocked by another component; the entry names it. */
  BLOCKED,

  /** The check has been failing longer than the grace period. */
  CHECK_ERROR,

  /** A tag that moves, running in a production environment. */
  MOVING_TAG_IN_PRODUCTION,

  /** Support ends within the warning threshold. */
  EOL_WARNING,

  /** A planned rollout still ahead — last, because it needs nothing today. */
  ROLLOUT_PLANNED
}
