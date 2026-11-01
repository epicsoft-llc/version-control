package one.epicsoft.vc.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true, description = """
    What a link of a component leads to.

    - `REPOSITORY`: the source code, on GitHub, GitLab, Codeberg or Bitbucket.
    - `IMAGE`: a container image, on Docker Hub, GHCR or Quay.
    - `PACKAGE`: a package source, such as an APT repository.
    - `HOMEPAGE`: the project's website.
    - `DOCUMENTATION`: the upgrade guide of the component.
    - `RELEASE_POLICY`: how long the project supports a release.
    - `END_OF_LIFE`: the product's page on endoflife.date.
    - `OTHER`: anything else, kept by hand.
    """)
public enum LinkKind {

  REPOSITORY,
  IMAGE,
  PACKAGE,
  HOMEPAGE,
  DOCUMENTATION,
  RELEASE_POLICY,
  END_OF_LIFE,
  OTHER
}
