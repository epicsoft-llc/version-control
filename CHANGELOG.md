# Changelog

All notable changes to this project are documented here, grouped as in
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). A release is headed by its tag and date,
`## [1.2.3] - YYYY-MM-DD`; changes from before the first tag are headed by their date alone.

## [0.2.1] - 2026-10-20

### Security
- Both images upgrade the Alpine packages of their base image when they are built, so a release carries the fixes
  published until then — 0.2.0 still shipped OpenSSL 3.5.8, expat 2.8.5 and an older zlib

## [0.2.0] - 2026-10-16

### Added
- A copy button behind every version number — matrix, task list, component page — puts exactly that version on the
  clipboard, without the whitespace that selecting it by hand picks up

## [0.1.2] - 2026-10-16

### Fixed
- A changed version rule — pattern, major line, prereleases, strategy — applies to the last check right away, from
  the versions it found, instead of at the next check up to two days later. Until then a component held on major 17
  was still offered 18 in the matrix

## [0.1.1] - 2026-10-16

### Fixed
- The matrix, the task list and the master data keep the project you chose — after a reload and on the way back
  from a component page, instead of starting on the first project again. A component page switches to its own
  project, so a link straight to it leads back into the right matrix

## [0.1.0] - 2026-10-16

### Added
- Mail as a channel for the daily notification, through any SMTP server — `VC_MAIL_HOST`, `_PORT`, `_SECURITY`
  (`starttls`, required rather than attempted, `tls` or `none`), `_USERNAME`, `_PASSWORD`, `_FROM` and `_TO` (comma
  separated). Off until host, sender and recipient are set
- `NotificationChannel` as the interface of all channels; a new channel is one implementation and a constant in
  `ChannelType`
- `deploy/compose.yml` and `.env.example` pass the `VC_MAIL_*` settings to the backend
- Dependency `spring-boot-starter-mail` for the SMTP channel
- This changelog; `CONTRIBUTING.md` asks for a line per change a user notices
- Releases are published to Docker Hub as `epicsoft/version-control` and get a GitHub release with the notes
  from this changelog. A release `1.2.3` is tagged `vc-core-1.2.3` and `vc-dashboard-1.2.3` and moves `-1.2`,
  `-1` and `-latest` along, in both registries
- `VC_IMAGE_REPOSITORY` chooses the registry the compose file pulls from

### Changed
- The daily notification goes to every configured channel and counts as reported only once all of them delivered
  it: a failing channel repeats the news on the others the next day instead of losing it for its own readers
- Gradle wrapper 9.8.1
- The README reads the same on GitLab, GitHub and Docker Hub: absolute links and images, where the images are,
  where to report issues and what the MIT license covers

### Fixed
- The first check of a component with packages (APT repository) ended in a server error instead of storing its
  result, so such a component never got a successful check

## 2026-09-03

### Added
- Inventory of the versions each component runs in each environment, with a version matrix that marks environments
  which disagree and tells a deliberately empty cell from one nobody has filled in
- Task list as the start page: out of support, update available, blocked, failing checks — in a fixed order
- Update checks against Docker Hub, GitHub releases and APT repositories, spread across the day; manual components
  without a source are a regular case, never an error
- Version rules per component, the largest allowed step (`maxStep`), upgrade stops and an upgrade path through
  intermediate versions
- End-of-life tracking from endoflife.date with warning and escalation thresholds
- Dependencies between components, evaluated per environment, with blocked updates naming the constraint
- History of every version change with note, backup reference, rollback path and downtime, and decisions that
  postpone an update until a follow-up date
- Daily Telegram summary of what is new; nothing is sent on a day without news
- Interface in English and German
- Images `vc-core` and `vc-dashboard` in the GitLab Container Registry, PostgreSQL schema through Flyway, and
  `deploy/compose.yml` to run all three
