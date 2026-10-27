package one.epicsoft.vc.exception;

import jakarta.annotation.Nonnull;
import java.io.Serial;
import java.util.UUID;

/** The request is well formed but asks for something the domain does not allow. */
public class AppValidationException extends AppException {

  @Serial
  private static final long serialVersionUID = 1L;

  protected AppValidationException(@Nonnull final String message, @Nonnull final String messageKey) {
    super(message, messageKey);
  }

  @Nonnull
  public static AppValidationException differentProjects(@Nonnull final UUID componentId, @Nonnull final UUID environmentId) {
    return new AppValidationException(
        "Component '%s' and environment '%s' belong to different projects".formatted(componentId, environmentId),
        "error.differentProjects");
  }

  @Nonnull
  public static AppValidationException projectCannotChange(@Nonnull final String type, @Nonnull final UUID id) {
    return new AppValidationException("%s '%s' cannot be moved to another project".formatted(type, id),
                                      "error.projectCannotChange");
  }

  @Nonnull
  public static AppValidationException parameterRequired(@Nonnull final String parameter) {
    return new AppValidationException("Request is missing '%s'".formatted(parameter), "error.badRequest");
  }

  /**
   * A patch that is not an object, names a field the resource does not have, or carries a value of the wrong
   * type. Only the place is logged, never the value: a patch can carry free text.
   */
  @Nonnull
  public static AppValidationException patchNotApplicable(@Nonnull final String type, @Nonnull final UUID id,
                                                          @Nonnull final String where) {
    return new AppValidationException("Patch for %s '%s' cannot be applied at %s".formatted(type, id, where),
                                      "error.badRequest");
  }

  @Nonnull
  public static AppValidationException duplicatePackage(@Nonnull final String name) {
    return new AppValidationException("Package '%s' is listed twice".formatted(name), "error.duplicatePackage");
  }

  /**
   * A prerequisite from another project would be evaluated against environments
   * that do not exist on the other side.
   */
  @Nonnull
  public static AppValidationException dependencyAcrossProjects(@Nonnull final UUID componentId,
                                                                @Nonnull final UUID dependsOnId) {
    return new AppValidationException("Component %s and its prerequisite %s belong to different projects"
                                          .formatted(componentId, dependsOnId),
                                      "error.dependencyAcrossProjects");
  }

  /**
   * A range nothing can read blocks every future update of the prerequisite and
   * says nothing about why — so it is refused where it is written, not carried
   * into the task list as a permanent unexplained block.
   */
  @Nonnull
  public static AppValidationException invalidVersionRange(@Nonnull final String range) {
    return new AppValidationException("Version range %s cannot be read".formatted(range),
                                      "error.invalidVersionRange");
  }

  /** A component that depends on itself would block every update of its own. */
  @Nonnull
  public static AppValidationException selfDependency(@Nonnull final UUID componentId) {
    return new AppValidationException("Component %s cannot depend on itself".formatted(componentId),
                                      "error.selfDependency");
  }

  /** Two ranges for the same prerequisite would reach the unique index as a 500. */
  @Nonnull
  public static AppValidationException duplicateDependency(@Nonnull final UUID dependsOnId) {
    return new AppValidationException("Component %s is listed twice as a prerequisite".formatted(dependsOnId),
                                      "error.duplicateDependency");
  }

  /** Two stops on the same version would reach the unique index as a 500. */
  @Nonnull
  public static AppValidationException duplicateUpgradeStop(@Nonnull final String version) {
    return new AppValidationException("Version '%s' is listed twice as an upgrade stop".formatted(version),
                                      "error.duplicateUpgradeStop");
  }

  @Nonnull
  public static AppValidationException unknownPackage(@Nonnull final String name) {
    return new AppValidationException("Package '%s' is not configured for this component".formatted(name),
                                      "error.unknownPackage");
  }

  /** An entry that records no change would be noise in the one list that must stay readable. */
  @Nonnull
  public static AppValidationException versionUnchanged(@Nonnull final UUID componentEnvironmentId,
                                                        @Nonnull final String version) {
    return new AppValidationException("Version '%s' of '%s' is already the recorded one"
                                          .formatted(version, componentEnvironmentId),
                                      "error.versionUnchanged");
  }

  /**
   * The minimum interval holds for a check somebody asked for by hand as well.
   * An exception for the manual case is the path by which the interval stops
   * existing — and the upstream sources are the ones that pay for it.
   */
  @Nonnull
  public static AppValidationException checkTooSoon(@Nonnull final UUID componentId, final long hours) {
    return new AppValidationException("Component '%s' was checked less than %d hours ago"
                                          .formatted(componentId, hours),
                                      "error.checkTooSoon");
  }

  /** A lookup somebody asked for, on an installation without a Dependency-Check database. */
  @Nonnull
  public static AppValidationException vulnerabilitiesNotConfigured() {
    return new AppValidationException("No Dependency-Check database is configured", "error.vulnerabilitiesNotConfigured");
  }

  /** BLOCKED comes from the dependency check; by hand it would never clear. */
  @Nonnull
  public static AppValidationException decisionNotSettable(@Nonnull final UUID componentId) {
    return new AppValidationException("State BLOCKED cannot be set by hand for component '%s'".formatted(componentId),
                                      "error.decisionNotSettable");
  }

  /**
   * A postponement without a date, or with one that has passed, is a note nobody
   * comes back to — which is the state this feature exists to replace.
   */
  @Nonnull
  public static AppValidationException snoozeDateRequired(@Nonnull final UUID componentId) {
    return new AppValidationException(
        "Postponing component '%s' needs a date in the future to come back on".formatted(componentId),
        "error.snoozeDateRequired");
  }

  /** A hold stays for good, so it has to say why — otherwise nobody dares to lift it later. */
  @Nonnull
  public static AppValidationException holdReasonRequired(@Nonnull final UUID componentId) {
    return new AppValidationException("Holding component '%s' needs a reason in the note".formatted(componentId),
                                      "error.holdReasonRequired");
  }

  /** A cell marked as deliberately absent cannot carry a deployed version. */
  @Nonnull
  public static AppValidationException inactiveCell(@Nonnull final UUID componentEnvironmentId) {
    return new AppValidationException("Component environment '%s' is inactive and holds no version"
                                          .formatted(componentEnvironmentId),
                                      "error.inactiveCell");
  }

  /** The same refusal for a cell that is being created and has no id yet. */
  @Nonnull
  public static AppValidationException inactiveCell(@Nonnull final UUID componentId, @Nonnull final UUID environmentId) {
    return new AppValidationException("Component '%s' is inactive in environment '%s' and holds no version"
                                          .formatted(componentId, environmentId),
                                      "error.inactiveCell");
  }

  /**
   * Changing a version that is already recorded belongs in the dialog that writes
   * the history entry in the same step — there is deliberately no second way in.
   */
  @Nonnull
  public static AppValidationException versionNeedsHistory(@Nonnull final UUID componentEnvironmentId) {
    return new AppValidationException(
        "Version of '%s' is already recorded and can only be changed through the version change dialog"
            .formatted(componentEnvironmentId),
        "error.versionNeedsHistory");
  }
}
