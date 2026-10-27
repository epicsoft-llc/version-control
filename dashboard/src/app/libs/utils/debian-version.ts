import {isDefined} from './vc.utils';

/**
 * A Debian package version as a reader needs it: `29.9.2-1` out of `5:29.9.2-1~ubuntu.24.04~noble`. The epoch only
 * orders, and the distribution names the build, not the release.
 *
 * Recognised by its form, the same way as on the server (`DebianVersion.shortForm`): an upstream version that starts
 * with a digit and no colon after the epoch. Image tags, release tags and digests come back exactly as they went in.
 */
export function shortDebianVersion(value: string): string {
  const trimmed: string = value.trim();
  const epoch: RegExpExecArray | null = /^(\d+):(.*)$/.exec(trimmed);
  const rest: string = isDefined(epoch) ? epoch[2] : trimmed;
  // The last hyphen splits, as in Debian: 2.1-beta-3 is upstream 2.1-beta with revision 3.
  const split: number = rest.lastIndexOf('-');
  const upstream: string = split < 0 ? rest : rest.substring(0, split);
  if (rest.includes(':') || !/^\d/.test(upstream)) {
    return value;
  }
  const revision: string = split < 0 ? '' : rest.substring(split + 1);
  // A tilde inside the revision starts the distribution; one in the upstream version is a pre-release and stays.
  const tilde: number = revision.indexOf('~');
  const packageRevision: string = tilde < 0 ? revision : revision.substring(0, tilde);
  return packageRevision.length === 0 ? upstream : `${upstream}-${packageRevision}`;
}
