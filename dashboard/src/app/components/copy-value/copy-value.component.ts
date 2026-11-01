import {ChangeDetectionStrategy, Component, Input, OnDestroy} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {isDefined} from '../../libs/utils/vc.utils';

type CopyState = 'idle' | 'copied' | 'failed';

/** Copies exactly the value; a selection by hand picks up the whitespace of the cell around it. */
@Component({
  // Default, explicitly: the state is a plain field written from a promise
  // callback, which OnPush would never render (CODE_CONVENTION.md).
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-copy-value',
  imports: [TranslatePipe],
  templateUrl: './copy-value.component.html'
})
export class CopyValueComponent implements OnDestroy {

  /** Long enough to be seen, short enough that the next copy starts from the plain icon. */
  private static readonly FEEDBACK_MS = 1500;

  @Input({required: true})
  public value!: string;

  protected state: CopyState = 'idle';

  private timer?: ReturnType<typeof setTimeout>;

  public ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  protected copy(event: Event): void {
    // The value often sits in a cell that opens a dialog; copying must not do that too.
    event.stopPropagation();
    // Absent outside a secure context, so the failure is answered here rather than thrown.
    if (isDefined(navigator.clipboard)) {
      navigator.clipboard.writeText(this.value.trim()).then(() => this.show('copied'), () => this.show('failed'));
    } else {
      this.show('failed');
    }
  }

  protected titleKey(): string {
    if (this.state === 'copied') {
      return 'copy.done';
    }
    return this.state === 'failed' ? 'copy.failed' : 'copy.title';
  }

  private show(state: CopyState): void {
    this.state = state;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.state = 'idle', CopyValueComponent.FEEDBACK_MS);
  }
}
