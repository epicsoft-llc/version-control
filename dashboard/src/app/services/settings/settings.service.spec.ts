import {of, throwError} from 'rxjs';
import {beforeEach, describe, expect, it} from 'vitest';
import {SettingsDto} from '../../repositories/dto/settings.dto';
import {SettingsService} from './settings.service';

/** Short by default, in full when the server asks for it, and still working when it cannot be asked. */
describe('SettingsService', () => {

  const DOCKER = '5:29.9.2-1~ubuntu.24.04~noble';

  let service: SettingsService;

  beforeEach(() => {
    service = new SettingsService();
  });

  it('writes a package version short before anything is loaded', () => {
    expect(service.label(DOCKER)).toBe('29.9.2-1');
  });

  it('writes it in full when the installation asks for that', () => {
    service.load(of(new SettingsDto({debianVersions: 'FULL'}))).subscribe();

    expect(service.label(DOCKER)).toBe(DOCKER);
  });

  it('keeps the default when the settings cannot be read', () => {
    let completed = false;
    service.load(throwError(() => new Error('offline'))).subscribe({complete: () => completed = true});

    // The start of the application waits for this; an error here would leave the page blank.
    expect(completed).toBe(true);
    expect(service.label(DOCKER)).toBe('29.9.2-1');
  });
});
