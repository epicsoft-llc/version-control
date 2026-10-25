package one.epicsoft.vc.jpa.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

/**
 * One change to a component: a field of its master data, a decision or a rollout plan. Written in the
 * transaction that makes the change, so what happened and its record cannot disagree.
 */
@Getter
@Setter
@Entity
@Table(name = "component_change")
public class ComponentChangeEntity extends AbstractEntity {

  @Nonnull
  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  /** The field as the API names it, or `decision` and `rolloutPlan`. */
  @Nonnull
  @Column(name = "field", nullable = false, length = 50)
  private String field;

  @Nullable
  @Column(name = "old_value", columnDefinition = "TEXT")
  private String oldValue;

  @Nullable
  @Column(name = "new_value", columnDefinition = "TEXT")
  private String newValue;

  @Nullable
  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Nonnull
  @ToString.Exclude
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
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
