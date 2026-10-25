import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {TranslatePipe} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {ComponentEnvironmentDto, RolloutPlanRequest} from '../../repositories/dto/component-environment.dto';
import {ComponentEnvironmentRepo} from '../../repositories/api/component-environment.repo';
import {nonBlank, unsubscribe} from '../../libs/utils/vc.utils';

/**
 * Plans a rollout for one environment: which version, on which day, and why.
 *
 * <p>The plan ends by itself once the version is recorded, so there is nothing to tidy up afterwards — and a day
 * that passes without it puts the plan on the task list.
 */
@Component({
  // Default, explicitly: this Angular version compiles a component as OnPush
  // unless told otherwise, and these components hold their state in plain
  // fields written from subscribe callbacks. Under OnPush such a view is
  // never marked dirty, so every page renders its heading and then stays
  // empty for good - with no error anywhere. Until the state is held in
  // signals, the strategy has to say what the code assumes.
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-plan-dialog',
  imports: [FormsModule, TranslatePipe],
  templateUrl: './plan-dialog.component.html'
})
export class PlanDialogComponent implements OnInit, OnDestroy {

  private readonly activeModal: NgbActiveModal = inject(NgbActiveModal);
  private readonly componentEnvironmentRepo: ComponentEnvironmentRepo = inject(ComponentEnvironmentRepo);
  private readonly subs: Subscription[] = [];

  /** Set by the opener before the dialog is shown. */
  public cell!: ComponentEnvironmentDto;
  public componentName = '';
  public environmentName = '';
  /** The proposed target, offered as the version until a plan says otherwise. */
  public suggestedVersion?: string;

  protected version = '';
  protected day = '';
  protected note = '';
  protected saving = false;
  protected errorMessage?: string;

  public ngOnInit(): void {
    this.version = this.cell.plannedVersion ?? this.suggestedVersion ?? '';
    this.day = this.cell.plannedFor ?? '';
    this.note = this.cell.plannedNote ?? '';
  }

  public ngOnDestroy(): void {
    unsubscribe(this.subs);
  }

  protected canSave(): boolean {
    return !this.saving && nonBlank(this.version) && nonBlank(this.day);
  }

  protected save(): void {
    if (!this.canSave()) {
      return;
    }
    this.saving = true;
    this.errorMessage = undefined;
    const request = new RolloutPlanRequest({
      plannedVersion: this.version.trim(),
      plannedFor: this.day,
      note: nonBlank(this.note) ? this.note.trim() : undefined
    });
    this.subs.push(this.componentEnvironmentRepo.plan(this.cell.id, request).subscribe({
      next: (cell) => this.activeModal.close(cell),
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
