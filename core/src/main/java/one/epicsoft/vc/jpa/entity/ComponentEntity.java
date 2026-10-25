package one.epicsoft.vc.jpa.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import one.epicsoft.vc.enums.MaxStep;
import one.epicsoft.vc.enums.SourceType;
import one.epicsoft.vc.enums.VersionStrategy;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.isTrue;

/** A watched piece of infrastructure — a database, a reverse proxy, a set of packages on a host. */
@Getter
@Setter
@Entity
@Table(name = "component")
public class ComponentEntity extends AbstractDeletableEntity {

  @Nonnull
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Nullable
  @Column(name = "description")
  private String description;

  // #### Source of the update check
  @Nonnull
  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 32)
  private SourceType sourceType;

  /** Image path, repository or the base URL of an APT repository, depending on the type. */
  @Nullable
  @Column(name = "source_ref", length = 500)
  private String sourceRef;

  @Nullable
  @Column(name = "apt_suite", length = 50)
  private String aptSuite;

  @Nullable
  @Column(name = "apt_component", length = 50)
  private String aptComponent;

  @Nullable
  @Column(name = "apt_architecture", length = 50)
  private String aptArchitecture;

  // #### Which candidates count
  /** Null means the default rule applies: the pattern is derived from the running tag. */
  @Nullable
  @Column(name = "tag_pattern", length = 200)
  private String tagPattern;

  @Nonnull
  @Enumerated(EnumType.STRING)
  @Column(name = "version_strategy", nullable = false, length = 32)
  private VersionStrategy versionStrategy;

  @Nullable
  @Column(name = "major_constraint", length = 50)
  private String majorConstraint;

  @Nonnull
  @Getter(AccessLevel.NONE)
  @Column(name = "allow_prerelease", nullable = false)
  private Boolean allowPrerelease;

  // #### How far one step may go
  @Nonnull
  @Enumerated(EnumType.STRING)
  @Column(name = "max_step", nullable = false, length = 16)
  private MaxStep maxStep;

  @Nullable
  @Column(name = "upgrade_path_notes")
  private String upgradePathNotes;

  @Nullable
  @Column(name = "upgrade_doc_url", length = 500)
  private String upgradeDocUrl;

  /** Where the release notes of one version are; {@code {version}} stands for the tag. */
  @Nullable
  @Column(name = "release_notes_url", length = 500)
  private String releaseNotesUrl;

  @Nonnull
  @Getter(AccessLevel.NONE)
  @Column(name = "needs_maintenance_window", nullable = false)
  private Boolean needsMaintenanceWindow;

  // #### End of life
  /** Null means no check at all — not an error and nothing to display. */
  @Nullable
  @Column(name = "eol_product", length = 100)
  private String eolProduct;

  @Nullable
  @Column(name = "eol_cycle", length = 50)
  private String eolCycle;

  // #### Check scheduling
  @Nonnull
  @Getter(AccessLevel.NONE)
  @Column(name = "check_enabled", nullable = false)
  private Boolean checkEnabled;

  @Nullable
  @Column(name = "next_check_due")
  private Instant nextCheckDue;

  @Nonnull
  @Column(name = "check_failure_count", nullable = false)
  private Integer checkFailureCount;

  @Nonnull
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private ProjectEntity project;

  @Nullable
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  // No @OrderBy: a Set is materialised into a HashSet and iterates in hash order
  // regardless. Ordered access goes through the repository.
  @OneToMany(mappedBy = "component", fetch = FetchType.LAZY)
  private Set<ComponentPackageEntity> packages;

  // #### Relation IDs
  @Nonnull
  @Setter(AccessLevel.NONE)
  @Column(name = "project_id", insertable = false, updatable = false)
  private UUID projectId;

  @PrePersist
  public void prePersistComponent() {
    if (isNull(this.allowPrerelease)) {
      this.allowPrerelease = Boolean.FALSE;
    }
    if (isNull(this.needsMaintenanceWindow)) {
      this.needsMaintenanceWindow = Boolean.FALSE;
    }
    if (isNull(this.checkEnabled)) {
      this.checkEnabled = Boolean.TRUE;
    }
    if (isNull(this.checkFailureCount)) {
      this.checkFailureCount = 0;
    }
    if (isNull(this.maxStep)) {
      this.maxStep = MaxStep.MINOR;
    }
    if (isNull(this.versionStrategy)) {
      this.versionStrategy = VersionStrategy.SEMVER;
    }
  }

  // #### Custom Getter and Setter
  public boolean isAllowPrerelease() {
    return isTrue(this.allowPrerelease);
  }

  public boolean isNeedsMaintenanceWindow() {
    return isTrue(this.needsMaintenanceWindow);
  }

  public boolean isCheckEnabled() {
    return isTrue(this.checkEnabled);
  }

  public void setProject(@Nonnull final ProjectEntity project) {
    this.project = project;
    this.projectId = project.getId();
  }
}
