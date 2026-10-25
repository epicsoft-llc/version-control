/** BLOCKED is derived from the dependency check and never set by hand. */
export type DecisionState = 'OPEN' | 'PLANNED' | 'POSTPONED' | 'SKIPPED' | 'HELD' | 'BLOCKED';

/** The states somebody can choose; BLOCKED is not among them on purpose. */
export const DECISION_STATES: DecisionState[] = ['OPEN', 'PLANNED', 'POSTPONED', 'SKIPPED', 'HELD'];

export class UpdateDecisionDto {

  public id!: string;
  public componentId!: string;
  public state!: DecisionState;
  /** The version this was decided about; a newer one reopens the decision. */
  public decidedForVersion?: string;
  public snoozeUntil?: string;
  public note?: string;
  public releaseUrl?: string;
  public decidedAt!: string;

  constructor(init?: Partial<UpdateDecisionDto>) {
    Object.assign(this, init);
  }
}

export class UpdateDecisionRequest {

  public componentId!: string;
  public state!: DecisionState;
  /** Required for a postponement — that is what makes it come back. */
  public snoozeUntil?: string;
  public note?: string;
  public releaseUrl?: string;

  constructor(init?: Partial<UpdateDecisionRequest>) {
    Object.assign(this, init);
  }
}
