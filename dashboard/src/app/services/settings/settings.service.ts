import {Injectable} from '@angular/core';
import {catchError, map, Observable, of, tap} from 'rxjs';
import {DebianVersionDisplay} from '../../repositories/dto/enums.dto';
import {SettingsDto} from '../../repositories/dto/settings.dto';
import {shortDebianVersion} from '../../libs/utils/debian-version';

/**
 * The settings of this installation, read once before the first page (`app.config.ts`). Read later, a version would
 * be written in full on one page and short on the next, depending on when the answer arrived — two versions where
 * there is one.
 */
@Injectable({providedIn: 'root'})
export class SettingsService {

  // The server's own default, so a failed request leaves the dashboard as it would be anyway.
  private debianVersions: DebianVersionDisplay = 'SHORT';

  public load(settings: Observable<SettingsDto>): Observable<void> {
    return settings.pipe(
      tap((received) => this.debianVersions = received.debianVersions),
      map(() => undefined),
      // Without its settings the dashboard still works, in the defaults; a blank page would not.
      catchError(() => of(undefined))
    );
  }

  /** How a version is written for the reader. Copy buttons, inputs and requests keep it as it was recorded. */
  public label(version: string): string {
    return this.debianVersions === 'SHORT' ? shortDebianVersion(version) : version;
  }
}
