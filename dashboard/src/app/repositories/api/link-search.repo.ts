import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UrlBuilder} from '../item/url.item';
import {LinkSearchDto} from '../dto/component-link.dto';

@Injectable({providedIn: 'root'})
export class LinkSearchRepo {

  private readonly http: HttpClient = inject(HttpClient);

  private readonly pathAll = '/v1/link-searches';

  public findByComponent(componentId: string): Observable<LinkSearchDto> {
    return this.http.get<LinkSearchDto>(UrlBuilder.builder()
                                                  .addPath(this.pathAll)
                                                  .addQuery('componentId', componentId)
                                                  .build().getUrl());
  }

  public searchNow(componentId: string): Observable<LinkSearchDto> {
    return this.http.post<LinkSearchDto>(UrlBuilder.builder()
                                                   .addPath(this.pathAll)
                                                   .addQuery('componentId', componentId)
                                                   .build().getUrl(), {});
  }
}
