import {ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection} from '@angular/core';
import {provideRouter, withComponentInputBinding} from '@angular/router';
import {provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {provideTranslateService} from '@ngx-translate/core';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';
import {routes} from './app.routes';
import {environment} from '../environments/environment';
import {I18nService} from './services/i18n/i18n.service';
import {SettingsRepo} from './repositories/api/settings.repo';
import {SettingsService} from './services/settings/settings.service';

export const appConfig: ApplicationConfig = {
  providers: [
    // Zone based change detection, declared rather than assumed: this Angular
    // version runs zoneless unless it is asked for, and zoneless with plain
    // component fields means every page renders its heading and then never
    // shows the data it loaded — the application looks empty and reports
    // nothing. The components here are written against zone.js, so this stays
    // until they are ported to signals.
    provideZoneChangeDetection({eventCoalescing: true}),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptorsFromDi()),
    provideTranslateService({
      fallbackLang: environment.i18n.defaultLang,
      loader: provideTranslateHttpLoader({prefix: './assets/i18n/', suffix: '.json'})
    }),
    provideAppInitializer(() => inject(I18nService).init()),
    provideAppInitializer(() => inject(SettingsService).load(inject(SettingsRepo).get()))
  ]
};
