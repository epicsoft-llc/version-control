import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UrlBuilder} from '../item/url.item';
import {ComponentChangeDto} from '../dto/component-change.dto';

/** What was changed on the components of a project, across all of them. */
@Injectable({providedIn: 'root'})
export class ComponentChangeRepo {

  private readonly http: HttpClient = inject(HttpClient);

  private readonly pathAll = '/v1/component-changes';

  public findRecentByProject(projectId: string, limit: number): Observable<ComponentChangeDto[]> {
    return this.http.get<ComponentChangeDto[]>(UrlBuilder.builder()
                                                         .addPath(this.pathAll)
                                                         .addQuery('projectId', projectId)
                                                         .addQuery('limit', limit)
                                                         .build().getUrl());
  }
}
