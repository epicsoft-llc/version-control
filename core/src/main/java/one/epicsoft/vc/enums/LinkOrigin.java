package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true, description = """
    Where a link of a component comes from.

    - `MANUAL`: kept by hand in the master data.
    - `SOURCE`: derived from the component's own fields — its source, end-of-life product and upgrade guide.
    - `END_OF_LIFE`: found by the link search in the product entry on endoflife.date.
    - `GITHUB`: found by the link search in a GitHub repository, its website.
    - `DOCKER_HUB`: found by the link search in the description of a Docker Hub repository.
    """)
public enum LinkOrigin {

  MANUAL,
  SOURCE,
  END_OF_LIFE,
  GITHUB,
  DOCKER_HUB
}
