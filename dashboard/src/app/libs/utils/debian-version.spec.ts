import {describe, expect, it} from 'vitest';
import {shortDebianVersion} from './debian-version';

/** The same cases as on the server: what a reader is shown has to match the daily message. */
describe('shortDebianVersion', () => {

  it('keeps the version and the package revision', () => {
    expect(shortDebianVersion('5:29.9.2-1~ubuntu.24.04~noble')).toBe('29.9.2-1');
    expect(shortDebianVersion('2.3.4-2')).toBe('2.3.4-2');
  });

  it('keeps a pre-release, which is part of the version', () => {
    expect(shortDebianVersion('1.0~rc1-1~deb12u1')).toBe('1.0~rc1-1');
  });

  it('leaves everything that is not a package version as it is', () => {
    // Image and release tags, a variant, a digest: an epoch or a distribution cannot occur in any of them.
    for (const version of ['v3.241.0', '18-alpine', '17.6', '1.2.3-rc.1', 'latest', 'sha256:0a1b2c3d']) {
      expect(shortDebianVersion(version)).toBe(version);
    }
  });
});
