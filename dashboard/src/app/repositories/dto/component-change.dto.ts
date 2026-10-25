/**
 * One change to a component: a field of its master data by its API name, `created`, `decision` or
 * `rolloutPlan`. The values are shown as they are stored — a list as its entries, separated by commas.
 */
export class ComponentChangeDto {

  public id!: string;
  public componentId!: string;
  public componentName!: string;
  public changedAt!: string;
  public field!: string;
  public oldValue?: string;
  public newValue?: string;
  public note?: string;

  constructor(init?: Partial<ComponentChangeDto>) {
    Object.assign(this, init);
  }
}
