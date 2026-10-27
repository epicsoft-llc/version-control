import {ChangeDetectionStrategy, Component, inject, Input} from '@angular/core';
import {SettingsService} from '../../services/settings/settings.service';
import {isDefined} from '../../libs/utils/vc.utils';

/**
 * A version as the reader is shown it, with the version as it was recorded on hover wherever the label leaves
 * something out. Only the text: the copy button next to it and every input keep the recorded version.
 */
@Component({
  // Default, explicitly: the inputs are plain fields, which OnPush would render once (CODE_CONVENTION.md).
  changeDetection: ChangeDetectionStrategy.Default,
  selector: 'vc-version',
  templateUrl: './version.component.html'
})
export class VersionComponent {

  private readonly settings: SettingsService = inject(SettingsService);

  @Input({required: true})
  public value: string | null | undefined;

  /** What stands in for a missing version, the dash of a table cell for instance. */
  @Input()
  public fallback = '';

  protected label(): string {
    return isDefined(this.value) ? this.settings.label(this.value) : this.fallback;
  }

  protected title(): string | null {
    return isDefined(this.value) && this.label() !== this.value ? this.value : null;
  }
}
