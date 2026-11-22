/** One entry of the change history. */
export class VersionChangeDto {

  public id!: string;
  public componentEnvironmentId!: string;
  public environmentId!: string;
  public componentId!: string;
  public fromVersion?: string;
  public toVersion!: string;
  public changedAt!: string;
  public note?: string;
  public backupTaken!: boolean;
  public backupRef?: string;
  public rollbackPath?: string;
  public downtimeMinutes?: number;
  public issues?: string;
  /** When the description was last corrected; absent while the entry reads as recorded. */
  public correctedAt?: string;

  constructor(init?: Partial<VersionChangeDto>) {
    Object.assign(this, init);
  }
}

/** The new version of one package of a component that has several. */
export class VersionChangePackageRequest {

  public packageName!: string;
  public version!: string;

  constructor(init?: Partial<VersionChangePackageRequest>) {
    Object.assign(this, init);
  }
}

/**
 * What the dialog sends: the new state and the story around it, saved in one
 * step. A component maintained per package sends `packages` instead of
 * `toVersion` and still produces a single entry.
 */
export class VersionChangeRequest {

  public componentEnvironmentId!: string;
  public toVersion?: string;
  public packages?: VersionChangePackageRequest[];
  public note?: string;
  public backupTaken?: boolean;
  public backupRef?: string;
  public rollbackPath?: string;
  public downtimeMinutes?: number;
  public issues?: string;

  constructor(init?: Partial<VersionChangeRequest>) {
    Object.assign(this, init);
  }
}
