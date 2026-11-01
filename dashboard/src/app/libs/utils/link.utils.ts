import {ComponentLinkDto} from '../../repositories/dto/component-link.dto';
import {LinkKind} from '../../repositories/dto/enums.dto';

/** Brand names, not texts to translate. */
const SITES: Record<string, string> = {
  'github.com': 'GitHub',
  'gitlab.com': 'GitLab',
  'codeberg.org': 'Codeberg',
  'bitbucket.org': 'Bitbucket',
  'hub.docker.com': 'Docker Hub',
  'ghcr.io': 'GitHub Container Registry',
  'quay.io': 'Quay',
  'endoflife.date': 'endoflife.date'
};

/** Bootstrap Icons has no Docker logo, so an image is a box on every registry. */
const SITE_ICONS: Record<string, string> = {
  'github.com': 'bi-github',
  'gitlab.com': 'bi-gitlab',
  'codeberg.org': 'bi-git',
  'bitbucket.org': 'bi-git'
};

const KIND_ICONS: Record<LinkKind, string> = {
  REPOSITORY: 'bi-git',
  IMAGE: 'bi-box-seam',
  PACKAGE: 'bi-box',
  HOMEPAGE: 'bi-globe',
  DOCUMENTATION: 'bi-book',
  RELEASE_POLICY: 'bi-shield-check',
  END_OF_LIFE: 'bi-hourglass-split',
  OTHER: 'bi-link-45deg'
};

export function linkHost(url: string): string {
  try {
    return new URL(url).hostname.toLowerCase().replace(/^www\./, '');
  } catch {
    return url;
  }
}

export function linkSite(url: string): string {
  const host = linkHost(url);
  return SITES[host] ?? host;
}

export function linkIcon(link: ComponentLinkDto): string {
  const host = linkHost(link.url);
  return link.kind !== 'IMAGE' && host in SITE_ICONS ? SITE_ICONS[host] : KIND_ICONS[link.kind];
}

/** Undefined for a support policy or an upgrade guide: their kind says more than the host. */
export function linkText(link: ComponentLinkDto): string | undefined {
  if (link.kind === 'DOCUMENTATION' || link.kind === 'RELEASE_POLICY') {
    return undefined;
  }
  if (link.kind === 'REPOSITORY' || link.kind === 'IMAGE') {
    const path = linkPath(link.url);
    return path.length > 0 ? path : linkSite(link.url);
  }
  return linkHost(link.url);
}

function linkPath(url: string): string {
  let path: string;
  try {
    path = new URL(url).pathname;
  } catch {
    return '';
  }
  const segments = path.split('/').filter((segment) => segment.length > 0);
  // Docker Hub puts official images under `_` and the rest under `r`, Quay everything under `repository`.
  if (['_', 'r', 'repository'].includes(segments[0] ?? '')) {
    segments.shift();
  }
  // GitLab nests groups one level deeper and ends the project path with `-`.
  const end = segments.indexOf('-');
  const project = end >= 0 ? segments.slice(0, end) : segments;
  return project.slice(0, linkHost(url) === 'gitlab.com' ? 3 : 2).join('/').replace(/\.git$/, '');
}
