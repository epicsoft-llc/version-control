import {EnvironmentDto} from './environment.dto';
import {MatrixCellState, SourceType} from './enums.dto';

/** One component in one environment, as the matrix shows it. */
export class MatrixCellDto {

  public environmentId!: string;
  /** Absent while the pair has no record — there is nothing to open yet. */
  public componentEnvironmentId?: string;
  public state!: MatrixCellState;
  public currentVersion?: string;
  public lastChangedAt?: string;
  /** The version of a planned rollout; absent while nothing is planned. */
  public plannedVersion?: string;
  public plannedFor?: string;
  public movingTag!: boolean;
  /** This version differs from what runs in production. */
  public deviating!: boolean;

  constructor(init?: Partial<MatrixCellDto>) {
    Object.assign(this, init);
  }
}

export class MatrixRowDto {

  public componentId!: string;
  public componentName!: string;
  public sourceType!: SourceType;
  public deviating!: boolean;
  /** One entry per environment, in the order of the environment list. */
  public cells: MatrixCellDto[] = [];

  constructor(init?: Partial<MatrixRowDto>) {
    Object.assign(this, init);
  }
}

/** Components as rows, environments as columns. */
export class MatrixDto {

  public projectId!: string;
  public environments: EnvironmentDto[] = [];
  public rows: MatrixRowDto[] = [];

  constructor(init?: Partial<MatrixDto>) {
    Object.assign(this, init);
  }
}
