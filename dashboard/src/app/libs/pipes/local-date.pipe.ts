import {formatDate, registerLocaleData} from '@angular/common';
import localeDe from '@angular/common/locales/de';
import {inject, Pipe, PipeTransform} from '@angular/core';
import {I18nService} from '../../services/i18n/i18n.service';
import {isDefined} from '../utils/vc.utils';

// English comes with Angular; every other language of the interface brings its locale data here.
registerLocaleData(localeDe);

/**
 * Angular's date pipe in the language the reader chose. The built-in one formats in LOCALE_ID, which is fixed
 * when the application starts, so a German page kept English dates. Impure, because switching the language
 * changes the result without changing the input; the last result is kept until one of the two changes.
 */
@Pipe({name: 'localDate', pure: false})
export class LocalDatePipe implements PipeTransform {

  private readonly i18n: I18nService = inject(I18nService);

  private lastKey: string | null = null;
  private lastResult: string | null = null;

  public transform(value: string | number | Date | null | undefined, format: string = 'mediumDate'): string | null {
    if (isDefined(value)) {
      const lang: string = this.i18n.getCurrent();
      const key: string = `${String(value)}|${format}|${lang}`;
      if (key !== this.lastKey) {
        this.lastKey = key;
        this.lastResult = formatDate(value, format, lang);
      }
      return this.lastResult;
    }
    return null;
  }
}
