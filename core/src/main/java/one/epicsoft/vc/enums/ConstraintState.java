package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether a dependency holds — with a third answer that has to stay visible.
 *
 * <p>A version that cannot be read is {@link #UNKNOWN} and never quietly
 * {@link #SATISFIED}. A moving tag or an unusual scheme is exactly the case
 * where nobody checked by hand either, and passing it silently is how an
 * unsupported combination runs for a year without anybody noticing.
 */
@Schema(enumAsRef = true, description = """
    Whether a dependency holds in an environment.

    - `SATISFIED`: the version of the prerequisite lies in the range.
    - `VIOLATED`: it does not.
    - `UNKNOWN`: the version cannot be read — a moving tag or an unusual scheme — and never counts as satisfied.
    """)
public enum ConstraintState {

  SATISFIED,
  VIOLATED,
  UNKNOWN
}
