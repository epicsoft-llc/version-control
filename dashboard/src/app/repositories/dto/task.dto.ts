import {DependencyCheckDto} from './dependency.dto';

/** The sections of the task list, in the order they are read. */
export type TaskSection =
  'EOL_EXCEEDED'
  | 'DEPENDENCY_VIOLATED'
  | 'ROLLOUT_OVERDUE'
  | 'UPDATE_OPEN'
  | 'SNOOZE_EXPIRED'
  | 'BLOCKED'
  | 'CHECK_ERROR'
  | 'MOVING_TAG_IN_PRODUCTION'
  | 'EOL_WARNING'
  | 'ROLLOUT_PLANNED';

/** One thing to do, with what it takes to judge it without opening anything. */
export class TaskEntryDto {

  public componentId!: string;
  public componentName!: string;
  public environmentName?: string;
  public currentVersion?: string;
  public latestVersion?: string;
  /** Free text of the reader's own making: a note, or a technical error message. */
  public detail?: string;
  /**
   * The constraints this entry is about — violated ones for a dependency, the
   * blocking ones for an update that cannot be done. The sentence is composed
   * here, in the reader's language, rather than in the backend.
   */
  public constraints: DependencyCheckDto[] = [];
  public since?: string;

  constructor(init?: Partial<TaskEntryDto>) {
    Object.assign(this, init);
  }
}

export class TaskSectionDto {

  public section!: TaskSection;
  public entries: TaskEntryDto[] = [];

  constructor(init?: Partial<TaskSectionDto>) {
    Object.assign(this, init);
  }
}

/** What has to be done in one project. Sections without entries are not sent. */
export class TaskListDto {

  public projectId!: string;
  public sections: TaskSectionDto[] = [];

  constructor(init?: Partial<TaskListDto>) {
    Object.assign(this, init);
  }
}
