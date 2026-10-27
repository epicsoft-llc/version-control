import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {TranslatePipe} from '@ngx-translate/core';
import {VersionComponent} from '../version/version.component';
import {Subscription} from 'rxjs';
import {ComponentDto} from '../../repositories/dto/component.dto';
import {ComponentEnvironmentDto} from '../../repositories/dto/component-environment.dto';
import {VersionChangePackageRequest, VersionChangeRequest} from '../../repositories/dto/version-change.dto';
import {VersionChangeRepo} from '../../repositories/api/version-change.repo';
import {isDefined, nonBlank, unsubscribe} from '../../libs/utils/vc.utils';

/** One version field, for a component that is maintained per package. */
export class PackageField {

  public packageName!: string;
  public currentVersion?: string;
  public version = '';

  constructor(init?: Partial<PackageField>) {
    Object.assign(this, init);
  }
}

/**
 * The dialog that changes a version — one form, one save.
 *
 * <p>Everything but the new version is optional and all of it is on the form.
 * Asked for in a second step, none of it would ever be filled in, and it is the
 * part that pays off the next time the same upgrade comes round.
 */
@Component({
  // Default, explicitly: this Angular version compiles a component as OnPush
  // unless told otherwise, and these components hold their state in plain
  // fields written from subscribe callbacks. Under OnPush such a view is
  // never marked dirty, so every page renders its heading and then stays
  // empty for good - with no error anywhere. Until the state is held in
  // signals, the strategy has to say what the code assumes.
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-version-change-dialog',
  imports: [FormsModule, TranslatePipe, VersionComponent],
  templateUrl: './version-change-dialog.component.html'
})
export class VersionChangeDialogComponent implements OnInit, OnDestroy {

  private readonly activeModal: NgbActiveModal = inject(NgbActiveModal);
  private readonly versionChangeRepo: VersionChangeRepo = inject(VersionChangeRepo);
  private readonly subs: Subscription[] = [];

  /** Set by the opener before the dialog is shown. */
  public component!: ComponentDto;
  public cell!: ComponentEnvironmentDto;
  public environmentName = '';

  protected packageFields: PackageField[] = [];
  protected toVersion = '';
  protected note = '';
  /** Starts unchecked on purpose: an uncomfortable checkbox gets read. */
  protected backupTaken = false;
  protected backupRef = '';
  protected rollbackPath = '';
  protected downtimeMinutes?: number;
  protected issues = '';
  protected saving = false;
  protected errorMessage?: string;

  public ngOnInit(): void {
    this.packageFields = this.component.packages
                             .map((entry) => new PackageField({
                               packageName: entry.name,
                               currentVersion: this.currentVersionOf(entry.name)
                             }));
  }

  public ngOnDestroy(): void {
    unsubscribe(this.subs);
  }

  protected hasPackages(): boolean {
    return this.packageFields.length > 0;
  }

  protected canSave(): boolean {
    if (this.saving) {
      return false;
    }
    return this.hasPackages()
           ? this.packageFields.some((field) => nonBlank(field.version))
           : nonBlank(this.toVersion);
  }

  protected save(): void {
    if (!this.canSave()) {
      return;
    }
    this.saving = true;
    this.errorMessage = undefined;
    this.subs.push(this.versionChangeRepo.changeVersion(this.buildRequest()).subscribe({
      next: (change) => this.activeModal.close(change),
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        // The server sends a translated sentence; showing the raw status instead
        // would hide the one thing that says what to do differently.
        this.errorMessage = error.error?.message ?? error.message;
      }
    }));
  }

  protected cancel(): void {
    this.activeModal.dismiss();
  }

  private buildRequest(): VersionChangeRequest {
    const request = new VersionChangeRequest({
      componentEnvironmentId: this.cell.id,
      note: this.trimmedOrUndefined(this.note),
      backupTaken: this.backupTaken,
      backupRef: this.trimmedOrUndefined(this.backupRef),
      rollbackPath: this.trimmedOrUndefined(this.rollbackPath),
      downtimeMinutes: isDefined(this.downtimeMinutes) ? this.downtimeMinutes : undefined,
      issues: this.trimmedOrUndefined(this.issues)
    });
    if (this.hasPackages()) {
      // Only the packages that were filled in; the rest keep the version they have.
      request.packages = this.packageFields
                             .filter((field) => nonBlank(field.version))
                             .map((field) => new VersionChangePackageRequest({
                               packageName: field.packageName,
                               version: field.version.trim()
                             }));
    } else {
      request.toVersion = this.toVersion.trim();
    }
    return request;
  }

  private currentVersionOf(packageName: string): string | undefined {
    return this.cell.packages.find((entry) => entry.packageName === packageName)?.currentVersion;
  }

  private trimmedOrUndefined(value: string): string | undefined {
    return nonBlank(value) ? value.trim() : undefined;
  }
}
