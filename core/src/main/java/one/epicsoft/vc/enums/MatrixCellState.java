package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one cell of the matrix says. The four are deliberately separate: a
 * component that is knowingly absent from an environment must not look like one
 * whose version nobody has written down.
 */
@Schema(enumAsRef = true, description = """
    What one cell of the matrix says.

    - `NOT_RECORDED`: no record — the pair has never been set up.
    - `INACTIVE`: set up and switched off; the component is deliberately not present in this environment.
    - `UNKNOWN`: present, but no version is recorded.
    - `RECORDED`: present with a known version.
    """)
public enum MatrixCellState {

  /** No record at all — the pair has never been set up. */
  NOT_RECORDED,

  /** Set up and switched off: deliberately not present in this environment. */
  INACTIVE,

  /** Runs here, but no version is recorded — a gap, and it should look like one. */
  UNKNOWN,

  /** Runs here with a known version. */
  RECORDED
}
