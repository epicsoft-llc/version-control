import {inject, Pipe, PipeTransform} from '@angular/core';
import {SettingsService} from '../../services/settings/settings.service';
import {isDefined} from '../utils/vc.utils';

/**
 * A version as the reader is shown it, for text that cannot hold `vc-version` — a title, for instance. Pure: the
 * settings are read before the first page and do not change while it is open.
 */
@Pipe({name: 'versionLabel'})
export class VersionLabelPipe implements PipeTransform {

  private readonly settings: SettingsService = inject(SettingsService);

  public transform(value: string | null | undefined): string {
    // Empty rather than null, so a template can fall back with `|| dash`.
    return isDefined(value) ? this.settings.label(value) : '';
  }
}
