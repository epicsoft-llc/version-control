import {MaxStep, SourceType, VersionStrategy} from './enums.dto';
import {UpgradeStopDto, UpgradeStopRequest} from './upgrade.dto';
import {ComponentDependencyDto, ComponentDependencyRequest} from './dependency.dto';

/** One package of a component that consists of several. */
export class ComponentPackageDto {

  public id!: string;
  public name!: string;
  public sortOrder!: number;
  public leadingPackage!: boolean;

  constructor(init?: Partial<ComponentPackageDto>) {
    Object.assign(this, init);
  }
}

/** A piece of deployed infrastructure — a database, a proxy, a monitoring stack. */
export class ComponentDto {

  public id!: string;
  public projectId!: string;
  public name!: string;
  public description?: string;
  public sourceType!: SourceType;
  public sourceRef?: string;
  public aptSuite?: string;
  public aptComponent?: string;
  public aptArchitecture?: string;
  public tagPattern?: string;
  public versionStrategy!: VersionStrategy;
  public majorConstraint?: string;
  public allowPrerelease!: boolean;
  public maxStep!: MaxStep;
  public upgradePathNotes?: string;
  public upgradeDocUrl?: string;
  /** Where the release notes of one version are; `{version}` stands for the tag. */
  public releaseNotesUrl?: string;
  public needsMaintenanceWindow!: boolean;
  public eolProduct?: string;
  public eolCycle?: string;
  public checkEnabled!: boolean;
  public packages: ComponentPackageDto[] = [];
  /** Versions that cannot be skipped on the way up. */
  public upgradeStops: UpgradeStopDto[] = [];
  /** What this component needs; the other direction is evaluated, not stored. */
  public dependencies: ComponentDependencyDto[] = [];

  constructor(init?: Partial<ComponentDto>) {
    Object.assign(this, init);
  }
}

export class ComponentPackageRequest {

  public name!: string;
  public sortOrder?: number;
  public leadingPackage?: boolean;

  constructor(init?: Partial<ComponentPackageRequest>) {
    Object.assign(this, init);
  }
}

export class ComponentRequest {

  public projectId!: string;
  public name!: string;
  public description?: string;
  public sourceType!: SourceType;
  public sourceRef?: string;
  public aptSuite?: string;
  public aptComponent?: string;
  public aptArchitecture?: string;
  public tagPattern?: string;
  public versionStrategy?: VersionStrategy;
  public majorConstraint?: string;
  public allowPrerelease?: boolean;
  public maxStep?: MaxStep;
  public upgradePathNotes?: string;
  public upgradeDocUrl?: string;
  public releaseNotesUrl?: string;
  public needsMaintenanceWindow?: boolean;
  public eolProduct?: string;
  public eolCycle?: string;
  public checkEnabled?: boolean;
  /** Omitted leaves the stored packages alone; an empty list clears them. */
  public packages?: ComponentPackageRequest[];
  /** Same shape for the stops: omitted keeps them, an empty list clears them. */
  public upgradeStops?: UpgradeStopRequest[];
  public dependencies?: ComponentDependencyRequest[];

  constructor(init?: Partial<ComponentRequest>) {
    Object.assign(this, init);
  }
}
