import {formatDate} from '@angular/common';
import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';
import {environment} from '../../../environments/environment';
import {I18nService} from '../../services/i18n/i18n.service';
import {LocalDatePipe} from './local-date.pipe';

/**
 * The date in the language the reader chose, and a change of language without a reload — the two things the
 * built-in pipe cannot do, because it formats in the LOCALE_ID fixed at startup.
 */
describe('LocalDatePipe', () => {

  // Noon UTC: the same calendar day in every time zone a test machine runs in.
  const DAY = '2026-10-24T12:00:00Z';

  let current: string;
  let pipe: LocalDatePipe;

  beforeEach(() => {
    current = 'en';
    TestBed.configureTestingModule({
      providers: [{provide: I18nService, useValue: {getCurrent: (): string => current}}]
    });
    pipe = TestBed.runInInjectionContext(() => new LocalDatePipe());
  });

  it('formats in the language the reader chose', () => {
    expect(pipe.transform(DAY, 'mediumDate')).toBe('Oct 24, 2026');

    current = 'de';

    // The same instance: a switch of the language reaches a page that stays open.
    expect(pipe.transform(DAY, 'mediumDate')).toBe('24.10.2026');
  });

  it('leaves an empty value empty', () => {
    // Templates fall back with `|| dash`, which only works when nothing comes out.
    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform(undefined)).toBeNull();
  });

  it('has locale data for every language of the interface', () => {
    // A language added to the environment without its locale data would break every page that shows a date.
    environment.i18n.langs.forEach((lang) => expect(() => formatDate(DAY, 'medium', lang), lang).not.toThrow());
  });
});
