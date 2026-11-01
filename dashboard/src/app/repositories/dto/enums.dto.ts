/** The enumerations the API sends, as literal unions — a wrong value fails to compile. */

export type SourceType = 'DOCKER_HUB' | 'GHCR' | 'GENERIC_REGISTRY' | 'GITHUB_RELEASE' | 'APT_REPOSITORY' | 'MANUAL';

export type VersionStrategy = 'LATEST_BY_DATE' | 'SEMVER' | 'DIGEST' | 'DEB_VERSION';

export type MaxStep = 'PATCH' | 'MINOR' | 'MAJOR' | 'NONE';

export type DebianVersionDisplay = 'SHORT' | 'FULL';

/**
 * What a cell of the matrix says. `INACTIVE` is "deliberately not present here"
 * and `UNKNOWN` is "runs here, but nobody wrote the version down" — the two must
 * never be drawn alike.
 */
export type MatrixCellState = 'NOT_RECORDED' | 'INACTIVE' | 'UNKNOWN' | 'RECORDED';

export const SOURCE_TYPES: SourceType[] =
  ['DOCKER_HUB', 'GHCR', 'GENERIC_REGISTRY', 'GITHUB_RELEASE', 'APT_REPOSITORY', 'MANUAL'];

export const VERSION_STRATEGIES: VersionStrategy[] = ['LATEST_BY_DATE', 'SEMVER', 'DIGEST', 'DEB_VERSION'];

export const MAX_STEPS: MaxStep[] = ['PATCH', 'MINOR', 'MAJOR', 'NONE'];

/** The server sends links grouped in this order. */
export type LinkKind = 'REPOSITORY' | 'IMAGE' | 'PACKAGE' | 'HOMEPAGE' | 'DOCUMENTATION' | 'RELEASE_POLICY' | 'END_OF_LIFE' | 'OTHER';

export type LinkOrigin = 'MANUAL' | 'SOURCE' | 'END_OF_LIFE' | 'GITHUB' | 'DOCKER_HUB';
