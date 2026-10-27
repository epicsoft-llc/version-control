import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UrlBuilder} from '../item/url.item';
import {SettingsDto} from '../dto/settings.dto';

/** The settings of this installation. Read only — they come from the configuration of the server. */
@Injectable({providedIn: 'root'})
export class SettingsRepo {

  private readonly http: HttpClient = inject(HttpClient);

  private readonly path = '/v1/settings';

  public get(): Observable<SettingsDto> {
    return this.http.get<SettingsDto>(UrlBuilder.builder()
                                                .addPath(this.path)
                                                .build().getUrl());
  }
}
