package one.epicsoft.vc.jpa.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import one.epicsoft.vc.enums.CheckState;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "link_search")
public class LinkSearchEntity extends AbstractEntity {

  @Nonnull
  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  @Nullable
  @Column(name = "next_check_due")
  private Instant nextCheckDue;

  @Nonnull
  @Enumerated(EnumType.STRING)
  @Column(name = "check_state", nullable = false, length = 32)
  private CheckState checkState;

  @Nullable
  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Nonnull
  @ToString.Exclude
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "component_id", nullable = false)
  private ComponentEntity component;

  // #### Relation IDs
  @Nonnull
  @Setter(AccessLevel.NONE)
  @Column(name = "component_id", insertable = false, updatable = false)
  private UUID componentId;

  // #### Custom Getter and Setter
  public void setComponent(@Nonnull final ComponentEntity component) {
    this.component = component;
    this.componentId = component.getId();
  }
}
