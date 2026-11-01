package one.epicsoft.vc.jpa.entity;

import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import one.epicsoft.vc.enums.LinkKind;
import one.epicsoft.vc.enums.LinkOrigin;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "component_link")
public class ComponentLinkEntity extends AbstractEntity {

  @Nonnull
  @Column(name = "url", nullable = false, length = 2000)
  private String url;

  @Nonnull
  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 32)
  private LinkKind kind;

  @Nonnull
  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, length = 32)
  private LinkOrigin origin;

  @Nonnull
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

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
