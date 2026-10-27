import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it} from 'vitest';
import {SettingsDto} from '../../repositories/dto/settings.dto';
import {SettingsService} from '../../services/settings/settings.service';
import {VersionLabelPipe} from './version-label.pipe';

describe('VersionLabelPipe', () => {

  let settings: SettingsService;
  let pipe: VersionLabelPipe;

  beforeEach(() => {
    settings = new SettingsService();
    TestBed.configureTestingModule({providers: [{provide: SettingsService, useValue: settings}]});
    pipe = TestBed.runInInjectionContext(() => new VersionLabelPipe());
  });

  it('writes a package version the way the installation is set up', () => {
    expect(pipe.transform('5:29.9.2-1~ubuntu.24.04~noble')).toBe('29.9.2-1');

    settings.load(of(new SettingsDto({debianVersions: 'FULL'}))).subscribe();

    expect(pipe.transform('5:29.9.2-1~ubuntu.24.04~noble')).toBe('5:29.9.2-1~ubuntu.24.04~noble');
  });

  it('leaves an empty value empty', () => {
    // Templates fall back with `|| dash`, which only works when nothing comes out.
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
