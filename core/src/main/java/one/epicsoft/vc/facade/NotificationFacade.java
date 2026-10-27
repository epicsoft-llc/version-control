package one.epicsoft.vc.facade;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.adapter.NotificationChannel;
import one.epicsoft.vc.config.DisplayProperties;
import one.epicsoft.vc.data.NotificationDraft;
import one.epicsoft.vc.data.NotificationMessage;
import one.epicsoft.vc.dto.DependencyCheckDto;
import one.epicsoft.vc.dto.ProjectDto;
import one.epicsoft.vc.dto.TaskEntryDto;
import one.epicsoft.vc.dto.TaskListDto;
import one.epicsoft.vc.dto.TaskSectionDto;
import one.epicsoft.vc.enums.TaskSection;
import one.epicsoft.vc.i18n.TranslationService;
import one.epicsoft.vc.jpa.entity.NotificationEntryEntity;
import one.epicsoft.vc.jpa.entity.ProjectEntity;
import one.epicsoft.vc.service.NotificationEntryService;
import one.epicsoft.vc.service.ProjectService;
import one.epicsoft.vc.version.VersionLabel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static one.epicsoft.vc.util.VcUtil.nonBlank;

/**
 * The daily message, and the reason there usually is none.
 *
 * <p>Two rules carry the whole thing. It reports what is <em>new</em> since the
 * last message, because the same twelve entries every morning are read for a
 * week and ignored after that. And when nothing is new, nothing is sent — an
 * empty notification teaches people not to look.
 *
 * <p>Collecting and remembering are separate steps on purpose, and the job puts
 * them either side of the send. Nothing is written down as reported until it has
 * actually gone out, so a failed send costs a day rather than a whole message,
 * and no database transaction waits on a foreign server in the meantime.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacade {

  /** Sections worth a message; the rest is visible on the screen anyway. */
  private static final Set<TaskSection> REPORTED = Set.of(TaskSection.EOL_EXCEEDED,
                                                          TaskSection.DEPENDENCY_VIOLATED,
                                                          TaskSection.ROLLOUT_OVERDUE,
                                                          TaskSection.UPDATE_OPEN,
                                                          TaskSection.SNOOZE_EXPIRED,
                                                          TaskSection.BLOCKED,
                                                          TaskSection.CHECK_ERROR);

  private final ProjectService projectService;
  private final ProjectFacade projectFacade;
  private final TaskListFacade taskListFacade;
  private final NotificationEntryService notificationEntryService;
  private final TranslationService translationService;
  private final DisplayProperties displayProperties;
  private final List<NotificationChannel> notificationChannels;

  /**
   * What is new across every project, and the keys that say so.
   *
   * <p>Also forgets what is no longer true: an entry that has disappeared from
   * the current state is dropped, so the same problem coming back later is news
   * again. Staying silent about a problem that returned would be the worse
   * mistake, and that bookkeeping is safe to keep whether or not a message goes
   * out afterwards.
   */
  @Nonnull
  @Transactional(rollbackFor = Throwable.class)
  public NotificationDraft collect() {
    if (this.notificationChannels.stream().noneMatch(NotificationChannel::isConfiguredForSending)) {
      // Nobody configured a channel, so nobody asked for notifications.
      return NotificationDraft.empty();
    }

    final StringBuilder message = new StringBuilder();
    final Map<UUID, List<String>> keysByProject = new LinkedHashMap<>();
    for (final ProjectDto project : this.projectFacade.findAll()) {
      final ProjectPart part = this.collectForProject(project);
      if (part.lines().isEmpty()) {
        continue;
      }
      message.append(message.isEmpty() ? "" : "\n\n")
             .append(project.getName()).append("\n")
             .append(String.join("\n", part.lines()));
      keysByProject.put(project.getId(), part.keys());
    }
    if (message.isEmpty()) {
      log.debug("Nothing new to report");
      return NotificationDraft.empty();
    }
    return new NotificationDraft(new NotificationMessage(this.translationService.get("notification.title"), message.toString()),
                                 keysByProject);
  }

  /** Writes down what has gone out, so tomorrow it is not news again. */
  @Transactional(rollbackFor = Throwable.class)
  public void remember(@Nonnull final NotificationDraft draft) {
    final Instant now = Instant.now();
    draft.keysByProject().forEach((projectId, keys) -> {
      final ProjectEntity project = this.projectService.getById(projectId);
      keys.forEach(key -> this.notificationEntryService.remember(project, key, now));
    });
  }

  /** What is new in one project, and what to write down once it has gone out. */
  @Nonnull
  private ProjectPart collectForProject(@Nonnull final ProjectDto project) {
    final TaskListDto tasks = this.taskListFacade.getByProject(project.getId());
    final Map<String, TaskEntryDto> current = new LinkedHashMap<>();
    final Map<String, TaskSection> sections = new LinkedHashMap<>();
    for (final TaskSectionDto section : tasks.getSections()) {
      if (!REPORTED.contains(section.getSection())) {
        continue;
      }
      for (final TaskEntryDto entry : section.getEntries()) {
        final String key = key(section.getSection(), entry);
        current.put(key, entry);
        sections.put(key, section.getSection());
      }
    }

    final Set<String> alreadyReported = this.notificationEntryService.findAllByProject(project.getId()).stream()
                                            .map(NotificationEntryEntity::getEntryKey)
                                            .collect(Collectors.toSet());
    final Set<String> gone = alreadyReported.stream()
                                            .filter(key -> !current.containsKey(key))
                                            .collect(Collectors.toSet());
    if (!gone.isEmpty()) {
      this.notificationEntryService.forget(project.getId(), gone);
    }

    final List<String> lines = new ArrayList<>();
    final List<String> keys = new ArrayList<>();
    current.forEach((key, entry) -> {
      if (alreadyReported.contains(key)) {
        return;
      }
      lines.add(this.line(sections.get(key), entry));
      keys.add(key);
    });
    return new ProjectPart(lines, keys);
  }

  /**
   * One line, built from message keys rather than from words in this file. A
   * chat message is visible text like any other, and visible text is translated.
   */
  @Nonnull
  private String line(@Nonnull final TaskSection section, @Nonnull final TaskEntryDto entry) {
    final String where = nonBlank(entry.getEnvironmentName()) ? " (" + entry.getEnvironmentName() + ")" : "";
    final String versions = nonBlank(entry.getLatestVersion())
                            ? " " + (nonBlank(entry.getCurrentVersion()) ? this.label(entry.getCurrentVersion()) + " → " : "")
                              + this.label(entry.getLatestVersion())
                            : "";
    return "• " + this.translationService.get("taskSection." + section.name()) + ": "
           + entry.getComponentName() + where + versions;
  }

  /** Written for the reader; the key below keeps the version as it was recorded. */
  @Nonnull
  private String label(@Nonnull final String version) {
    return VersionLabel.of(version, this.displayProperties.getDebianVersions());
  }

  /**
   * What makes two reports the same report.
   *
   * <p>The version is part of it: an update to 17.4 that was reported and then
   * superseded by 17.6 is a new thing to say, not the same one again. So are the
   * constraints — one component can violate two of them in the same environment,
   * and a key that cannot tell them apart would report one and swallow the other
   * for good.
   */
  @Nonnull
  private static String key(@Nonnull final TaskSection section, @Nonnull final TaskEntryDto entry) {
    final String constraints = entry.getConstraints().stream()
                                    .map(DependencyCheckDto::getDependencyId)
                                    .map(UUID::toString)
                                    .sorted()
                                    .collect(Collectors.joining(","));
    return section.name() + "/" + entry.getComponentId() + "/"
           + (nonBlank(entry.getEnvironmentName()) ? entry.getEnvironmentName() : "-") + "/"
           + (nonBlank(entry.getLatestVersion()) ? entry.getLatestVersion() : "-") + "/"
           + (constraints.isEmpty() ? "-" : constraints);
  }

  /** The lines of one project, and the keys to remember once they have gone out. */
  private record ProjectPart(@Nonnull List<String> lines, @Nonnull List<String> keys) {
  }
}
