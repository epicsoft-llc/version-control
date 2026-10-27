import {ChangeDetectionStrategy, Component, inject, OnDestroy} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {TranslatePipe} from '@ngx-translate/core';
import {VersionComponent} from '../version/version.component';
import {Subscription} from 'rxjs';
import {DECISION_STATES, DecisionState, UpdateDecisionRequest} from '../../repositories/dto/update-decision.dto';
import {TaskRepo} from '../../repositories/api/task.repo';
import {nonBlank, unsubscribe} from '../../libs/utils/vc.utils';

/**
 * What to do about an available update, and why.
 *
 * <p>A note on its own would not do: "why did we not move" needs a state and,
 * for a postponement, a date to come back on. Without the date there is a
 * sentence sitting in the inventory three months later that nobody checks.
 */
@Component({
  // Default, explicitly: this Angular version compiles a component as OnPush
  // unless told otherwise, and these components hold their state in plain
  // fields written from subscribe callbacks. Under OnPush such a view is
  // never marked dirty, so every page renders its heading and then stays
  // empty for good - with no error anywhere. Until the state is held in
  // signals, the strategy has to say what the code assumes.
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-decision-dialog',
  imports: [FormsModule, TranslatePipe, VersionComponent],
  templateUrl: './decision-dialog.component.html'
})
export class DecisionDialogComponent implements OnDestroy {

  private readonly activeModal: NgbActiveModal = inject(NgbActiveModal);
  private readonly taskRepo: TaskRepo = inject(TaskRepo);
  private readonly subs: Subscription[] = [];

  /** Set by the opener before the dialog is shown. */
  public componentId!: string;
  public componentName = '';
  public latestVersion?: string;

  /** BLOCKED is not offered: it comes from the dependency check. */
  protected readonly states = DECISION_STATES;

  protected state: DecisionState = 'PLANNED';
  protected snoozeUntil = '';
  protected note = '';
  protected releaseUrl = '';
  protected saving = false;
  protected errorMessage?: string;

  public ngOnDestroy(): void {
    unsubscribe(this.subs);
  }

  protected needsDate(): boolean {
    return this.state === 'POSTPONED';
  }

  /** A hold stays for good; without the reason nobody dares to lift it later. */
  protected needsReason(): boolean {
    return this.state === 'HELD';
  }

  /** Today, so the date field cannot offer a day that is already over. */
  protected today(): string {
    const now = new Date();
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  }

  protected canSave(): boolean {
    return !this.saving && (!this.needsDate() || nonBlank(this.snoozeUntil)) && (!this.needsReason() || nonBlank(this.note));
  }

  protected save(): void {
    if (!this.canSave()) {
      return;
    }
    this.saving = true;
    this.errorMessage = undefined;
    const request = new UpdateDecisionRequest({
      componentId: this.componentId,
      state: this.state,
      // The end of the chosen day in the reader's own time zone. Parsing the
      // bare date would make it UTC midnight, so picking today would store a
      // moment that has already passed and the entry would be back on the list
      // before the dialog closes.
      snoozeUntil: this.needsDate() ? new Date(this.snoozeUntil + 'T23:59:59').toISOString() : undefined,
      note: nonBlank(this.note) ? this.note.trim() : undefined,
      releaseUrl: nonBlank(this.releaseUrl) ? this.releaseUrl.trim() : undefined
    });
    this.subs.push(this.taskRepo.decide(request).subscribe({
      next: (decision) => this.activeModal.close(decision),
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.errorMessage = error.error?.message ?? error.message;
      }
    }));
  }

  protected cancel(): void {
    this.activeModal.dismiss();
  }
}
