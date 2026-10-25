package one.epicsoft.vc.jpa.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import one.epicsoft.vc.enums.VersionStrategy;
import one.epicsoft.vc.version.TagRule;
import one.epicsoft.vc.version.VersionComparison;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.isEquals;
import static one.epicsoft.vc.util.VcUtil.isTrue;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/** The version of one component in one environment. */
@Getter
@Setter
@Entity
@Table(name = "component_environment")
public class ComponentEnvironmentEntity extends AbstractDeletableEntity {

  /**
   * False means "deliberately not present here" — a different statement from an
   * unknown version, and the matrix must show the two differently: a disabled
   * cell reads as neutral, a missing version as a gap.
   */
  @Nonnull
  @Getter(AccessLevel.NONE)
  @Column(name = "active", nullable = false)
  private Boolean active;

  /** The tag that is actually deployed; null while it is unknown. */
  @Nullable
  @Column(name = "current_version", length = 200)
  private String currentVersion;

  @Nullable
  @Column(name = "current_digest", length = 100)
  private String currentDigest;

  @Nullable
  @Column(name = "last_changed_at")
  private Instant lastChangedAt;

  /** The version a rollout is planned to; null while nothing is planned. */
  @Nullable
  @Column(name = "planned_version", length = 200)
  private String plannedVersion;

  @Nullable
  @Column(name = "planned_for")
  private LocalDate plannedFor;

  @Nullable
  @Column(name = "planned_note", columnDefinition = "TEXT")
  private String plannedNote;

  @Nonnull
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "component_id", nullable = false)
  private ComponentEntity component;

  @Nonnull
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "environment_id", nullable = false)
  private EnvironmentEntity environment;

  @Nullable
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @OneToMany(mappedBy = "componentEnvironment", fetch = FetchType.LAZY)
  private Set<ComponentEnvironmentPackageEntity> packages;

  // #### Relation IDs
  @Nonnull
  @Setter(AccessLevel.NONE)
  @Column(name = "component_id", insertable = false, updatable = false)
  private UUID componentId;

  @Nonnull
  @Setter(AccessLevel.NONE)
  @Column(name = "environment_id", insertable = false, updatable = false)
  private UUID environmentId;

  @PrePersist
  public void prePersistComponentEnvironment() {
    if (isNull(this.active)) {
      this.active = Boolean.TRUE;
    }
  }

  // #### Helper
  /**
   * True when the component runs here but nobody has recorded which version. The
   * matrix shows this as a gap; an inactive cell is not one.
   */
  public boolean isVersionUnknown() {
    return this.isActive() && !nonBlank(this.currentVersion);
  }

  /**
   * A tag that is not a plain version number — latest, stable, 17-alpine. Derived
   * rather than stored: a separate field would drift from the value it describes.
   * The rule itself lives in {@link TagRule}, because the dependency check has to
   * ask the same question.
   */
  public boolean isMovingTag() {
    return TagRule.isMovingTag(this.currentVersion);
  }

  public boolean hasPlan() {
    return nonBlank(this.plannedVersion);
  }

  /** The planned version or a newer one is recorded — the plan has done its job. */
  public boolean isPlanReached(@Nonnull final VersionStrategy strategy) {
    return this.hasPlan() && nonBlank(this.currentVersion)
           && (isEquals(this.currentVersion, this.plannedVersion)
               || !VersionComparison.isBehind(this.currentVersion, this.plannedVersion, strategy));
  }

  public void clearPlan() {
    this.plannedVersion = null;
    this.plannedFor = null;
    this.plannedNote = null;
  }

  // #### Custom Getter and Setter
  public boolean isActive() {
    return isTrue(this.active);
  }

  public void setComponent(@Nonnull final ComponentEntity component) {
    this.component = component;
    this.componentId = component.getId();
  }

  public void setEnvironment(@Nonnull final EnvironmentEntity environment) {
    this.environment = environment;
    this.environmentId = environment.getId();
  }
}
