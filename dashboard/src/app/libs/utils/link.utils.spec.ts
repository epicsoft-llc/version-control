import {describe, expect, it} from 'vitest';
import {ComponentLinkDto} from '../../repositories/dto/component-link.dto';
import {linkHost, linkIcon, linkSite, linkText} from './link.utils';

const link = (url: string, kind: ComponentLinkDto['kind']) => new ComponentLinkDto({url: url, kind: kind, origin: 'SOURCE'});

describe('link utils', () => {

  it('names a known site and falls back to the host', () => {
    expect(linkSite('https://hub.docker.com/_/postgres')).toBe('Docker Hub');
    expect(linkSite('https://www.github.com/traefik/traefik')).toBe('GitHub');
    expect(linkSite('https://www.postgresql.org/support/versioning/')).toBe('postgresql.org');
    expect(linkHost('not an address')).toBe('not an address');
  });

  it('shows the mark of a code site and what an image or page is otherwise', () => {
    expect(linkIcon(link('https://github.com/traefik/traefik', 'REPOSITORY'))).toBe('bi-github');
    expect(linkIcon(link('https://gitlab.com/acme/widget', 'REPOSITORY'))).toBe('bi-gitlab');
    expect(linkIcon(link('https://codeberg.org/acme/widget', 'REPOSITORY'))).toBe('bi-git');
    expect(linkIcon(link('https://hub.docker.com/_/postgres', 'IMAGE'))).toBe('bi-box-seam');
    expect(linkIcon(link('https://ghcr.io/acme/widget', 'IMAGE'))).toBe('bi-box-seam');
    expect(linkIcon(link('https://grafana.com', 'HOMEPAGE'))).toBe('bi-globe');
    expect(linkIcon(link('https://endoflife.date/postgresql', 'END_OF_LIFE'))).toBe('bi-hourglass-split');
    expect(linkIcon(link('https://wiki.example.com', 'OTHER'))).toBe('bi-link-45deg');
  });

  it('writes the path of a repository or an image and the host of the rest', () => {
    expect(linkText(link('https://github.com/traefik/traefik', 'REPOSITORY'))).toBe('traefik/traefik');
    expect(linkText(link('https://github.com/acme/widget/tree/main/docs', 'REPOSITORY'))).toBe('acme/widget');
    expect(linkText(link('https://gitlab.com/acme/tools/widget/-/releases', 'REPOSITORY'))).toBe('acme/tools/widget');
    expect(linkText(link('https://hub.docker.com/_/postgres', 'IMAGE'))).toBe('postgres');
    expect(linkText(link('https://hub.docker.com/r/grafana/grafana/tags', 'IMAGE'))).toBe('grafana/grafana');
    expect(linkText(link('https://quay.io/repository/acme/widget', 'IMAGE'))).toBe('acme/widget');
    expect(linkText(link('https://www.grafana.com/', 'HOMEPAGE'))).toBe('grafana.com');
    expect(linkText(link('https://endoflife.date/postgresql', 'END_OF_LIFE'))).toBe('endoflife.date');
  });

  it('leaves the text to the kind where the host says less', () => {
    expect(linkText(link('https://www.postgresql.org/support/versioning/', 'RELEASE_POLICY'))).toBeUndefined();
    expect(linkText(link('https://example.com/upgrade', 'DOCUMENTATION'))).toBeUndefined();
  });
});
