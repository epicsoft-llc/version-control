import {DependencyCheckDto} from './dependency.dto';

/** A version that cannot be skipped on the way up. */
export class UpgradeStopDto {

  public id!: string;
  public version!: string;
  /** Why it cannot be skipped — the sentence that saves the next reader an hour. */
  public note?: string;

  constructor(init?: Partial<UpgradeStopDto>) {
    Object.assign(this, init);
  }
}

export class UpgradeStopRequest {

  public version!: string;
  public note?: string;

  constructor(init?: Partial<UpgradeStopRequest>) {
    Object.assign(this, init);
  }
}

/** A version on the way to the target, with its release notes where the component says where they are. */
export class ReleaseLinkDto {

  public version!: string;
  public url?: string;

  constructor(init?: Partial<ReleaseLinkDto>) {
    Object.assign(this, init);
  }
}

/**
 * What can be done in one environment, and what merely exists beyond it.
 *
 * <p>Only `target` is something to act on. `beyond` is a note that a larger jump
 * is available and needs planning — shown as a demand it would be a permanent
 * red state nobody can resolve.
 */
export class UpgradeProposalDto {

  /** What stands in the way of the target; empty means nothing does. */
  public blockedBy: DependencyCheckDto[] = [];

  public environmentId!: string;
  public environmentName!: string;
  public currentVersion?: string;
  public target?: string;
  public beyond?: string;
  public stops: UpgradeStopDto[] = [];
  /** Every version after the running one up to the target, oldest first. */
  public releases: ReleaseLinkDto[] = [];
  public upgradePathNotes?: string;
  public upgradeDocUrl?: string;
  public needsMaintenanceWindow!: boolean;

  constructor(init?: Partial<UpgradeProposalDto>) {
    Object.assign(this, init);
  }
}
