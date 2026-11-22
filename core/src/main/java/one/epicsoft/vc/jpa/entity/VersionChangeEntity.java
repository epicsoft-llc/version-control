package one.epicsoft.vc.jpa.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.isNull;
import static one.epicsoft.vc.util.VcUtil.isTrue;

/**
 * One recorded version change, and what was learned while making it.
 *
 * <p>These extra fields sit here rather than on the component on purpose: they
 * describe a single maintenance action, and the next time the same upgrade comes
 * round this entry is what gets read.
 *
 * <p>An entry is written only by the version change facade, in the same
 * transaction as the version it describes. There is no second way in — a
 * separate "maintain the history" step would be the first one to be skipped.
 * Afterwards only its description can be corrected, through the same facade;
 * versions and time stay as recorded, and {@code updated} tells when.
 */
@Getter
@Setter
@Entity
@Table(name = "version_change")
public class VersionChangeEntity extends AbstractEntity {

  /** Null when nothing was recorded before — the first entry of a cell. */
  @Nullable
  @Column(name = "from_version", columnDefinition = "TEXT")
  private String fromVersion;

  @Nonnull
  @Column(name = "to_version", nullable = false, columnDefinition = "TEXT")
  private String toVersion;

  @Nonnull
  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  @Nullable
  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Nonnull
  @Getter(AccessLevel.NONE)
  @Column(name = "backup_taken", nullable = false)
  private Boolean backupTaken;

  @Nullable
  @Column(name = "backup_ref", length = 500)
  private String backupRef;

  @Nullable
  @Column(name = "rollback_path", columnDefinition = "TEXT")
  private String rollbackPath;

  @Nullable
  @Column(name = "downtime_minutes")
  private Integer downtimeMinutes;

  @Nullable
  @Column(name = "issues", columnDefinition = "TEXT")
  private String issues;

  @Nonnull
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "component_environment_id", nullable = false)
  private ComponentEnvironmentEntity componentEnvironment;

  // #### Relation IDs
  @Nonnull
  @Setter(AccessLevel.NONE)
  @Column(name = "component_environment_id", insertable = false, updatable = false)
  private UUID componentEnvironmentId;

  @PrePersist
  public void prePersistVersionChange() {
    if (isNull(this.backupTaken)) {
      this.backupTaken = Boolean.FALSE;
    }
    if (isNull(this.changedAt)) {
      this.changedAt = Instant.now();
    }
  }

  // #### Custom Getter and Setter
  public boolean isBackupTaken() {
    return isTrue(this.backupTaken);
  }

  public void setComponentEnvironment(@Nonnull final ComponentEnvironmentEntity componentEnvironment) {
    this.componentEnvironment = componentEnvironment;
    this.componentEnvironmentId = componentEnvironment.getId();
  }
}
