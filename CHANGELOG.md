# Changelog

All notable changes to this project are documented here, grouped as in
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). A release is headed by its tag and date,
`## [1.2.3] - YYYY-MM-DD`; changes from before the first tag are headed by their date alone.

## [0.7.0] - 2026-10-27

### Changed
- Debian package versions are written short, `29.9.2-1` instead of `5:29.9.2-1~ubuntu.24.04~noble`, on every page and
  in the daily notification. Hovering shows the version as it was recorded; copy buttons, inputs and the API keep it
  complete. Image tags, release tags and digests are written as before

### Added
- `VC_DISPLAY_DEBIAN_VERSIONS=full` writes Debian package versions in full again; `GET /api/v1/settings` tells a client
  which form the installation uses

## [0.6.3] - 2026-10-26

### Fixed
- The matrix no longer marks a Debian package as deviating when only the distribution it was built for differs:
  `5:29.9.2-1~ubuntu.24.04~noble` and `5:29.9.2-1~ubuntu.26.04~resolute` are the same version. Another upstream
  version or package revision is still marked

## [0.6.2] - 2026-10-26

### Fixed
- Clearing the end-of-life product of a component, or changing its product or cycle, drops the end of support
  looked up before. A cleared product left the old date on the task list and in the daily notification for good;
  the leftovers are removed on start

## [0.6.1] - 2026-10-25

### Changed
- A component that updates itself shows no versions: the matrix marks its environments with the circular arrow
  instead of the version recorded last, without a deviation, and its page leaves out the proposal and the buttons
  to change a version or plan a rollout. The history stays

## [0.6.0] - 2026-10-25

### Added
- The decision `AUTO_UPDATE` for a component that updates itself — an updater on the host installs every new
  version, so none of them is a task. Like `HELD` it needs no newer version to be decided again, but it asks for
  no reason; the matrix and the component page mark it with a circular arrow

## [0.5.2] - 2026-10-25

### Fixed
- The translations and the icon are asked for again on every load: they keep their names from release to
  release, and a browser that had cached an older file showed a newly added key as the key itself — the icon
  was even marked as unchanging for a year

## [0.5.1] - 2026-10-25

### Fixed
- Dates follow the language of the interface: the German page wrote them in English, "Oct 24, 2026" instead of
  "24.10.2026", because Angular's date pipe formats in the language fixed when the application starts

## [0.5.0] - 2026-10-25

### Added
- Dependency `@fontsource-variable/inter` 5.3.0 — the typeface comes with the dashboard, no font service is
  called

### Changed
- A new look: other colours, Inter as the typeface and calmer shapes — a dark header, every section of a page
  on a card of its own, buttons as pills, the versions in the matrix as chips. A long list of versions on the
  way to the target stays folded until it is opened
- The translations are maintained in the repository: the JSON files are the source, written by hand like
  code, and no build fetches them any more. `./gradlew :core:check` and `npm run i18n:verify` make sure every
  language carries every key with a text and the same placeholders, one key per line in alphabetical order,
  and that no key is left over which the code no longer uses

### Removed
- The Gradle plugin `one.epicsoft.tolgee`, the pull script of the dashboard and the variables `VC_TOLGEE_URL`,
  `VC_TOLGEE_PROJECT_ID` and `VC_TOLGEE_API_KEY`

### Fixed
- The versions on the way to the target wrap instead of pushing the component page sideways
- The API description no longer looks its texts up as message keys: since 0.3.0 every request of
  `/api/v1/openapi.json` wrote a few hundred error lines into the log, and every start a warning that the
  endpoint is on

## [0.4.0] - 2026-10-25

### Added
- A component can be held on its version for good: the decision `HELD` needs a reason, no newer version reopens it, and
  the matrix and the component page show it with a lock and the reason. The decision dialog is on the component page as
  well now, for components that are not on the task list
- Rollout plans per environment — version, day and note, planned from the component page or through
  `PUT /api/v1/component-environments/{id}/plan`. A plan ends by itself once its version, or a newer one, is recorded.
  Until then the task list shows it last, under "Rollout planned"; once the day has passed it moves up to "Rollout
  overdue", which the daily notification reports
- The upgrade proposal lists every version between the running one and the target, oldest first, each linked to its
  release notes: by itself for GitHub releases, otherwise through the new component field `releaseNotesUrl`, in which
  `{version}` stands for the tag
- A change log per component: every changed field of its master data, every decision and every rollout plan, with the
  value before and after (`GET /api/v1/components/{id}/changes`). The component page shows it, and the end of the task
  list shows the latest changes of the project (`GET /api/v1/component-changes`)
- Migration `V0.4.0` with the columns of the rollout plan, the release notes URL and the table `component_change`

### Changed
- Spring Boot 4.1.2; Angular CLI and Angular build 22.2.4

## [0.3.0] - 2026-10-25

### Added
- The API describes itself: an OpenAPI 3.1 document at `/api/v1/openapi.json`, with a summary for every operation,
  the meaning of every enum value — what `SKIPPED` or `POSTPONED` do to the task list, what `checkEnabled` stops —
  and the error shape every operation shares. Without the Swagger UI
- `PATCH /api/v1/components/{id}` changes single fields of a component as a JSON merge patch (RFC 7386): a field that
  is left out keeps its value, `null` clears it, a list replaces the stored one. A field the component does not have is
  refused rather than ignored
- Dependency `springdoc-openapi-starter-webmvc-api` 3.2.0 for the API description

### Fixed
- The packages of a component are validated without the Hibernate Validator warning about `@Valid` on a list

## [0.2.2] - 2026-10-20

### Security
- The dashboard is built with Angular 22.2.3, which fixes a denial of service in the router's server-side rendering —
  the dashboard renders in the browser only and was not affected

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
