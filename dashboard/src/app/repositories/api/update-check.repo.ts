import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UrlBuilder} from '../item/url.item';
import {UpdateCheckDto, VersionPreviewDto, VersionPreviewRequest} from '../dto/update-check.dto';

/** What the last check found, and the two ways of asking for more. */
@Injectable({providedIn: 'root'})
export class UpdateCheckRepo {

  private readonly http: HttpClient = inject(HttpClient);

  private readonly pathAll = '/v1/update-checks';
  private readonly pathPreview = '/v1/update-checks/preview';

  /** A component that was never checked answers with an empty list, not an error. */
  public findAllByProject(projectId: string): Observable<UpdateCheckDto[]> {
    return this.http.get<UpdateCheckDto[]>(UrlBuilder.builder()
                                                     .addPath(this.pathAll)
                                                     .addQuery('projectId', projectId)
                                                     .build().getUrl());
  }

  public findAllByComponent(componentId: string): Observable<UpdateCheckDto[]> {
    return this.http.get<UpdateCheckDto[]>(UrlBuilder.builder()
                                                     .addPath(this.pathAll)
                                                     .addQuery('componentId', componentId)
                                                     .build().getUrl());
  }

  public checkNow(componentId: string): Observable<UpdateCheckDto> {
    return this.http.post<UpdateCheckDto>(UrlBuilder.builder()
                                                    .addPath(this.pathAll)
                                                    .addQuery('componentId', componentId)
                                                    .build().getUrl(), {});
  }

  public preview(request: VersionPreviewRequest): Observable<VersionPreviewDto> {
    return this.http.post<VersionPreviewDto>(UrlBuilder.builder().addPath(this.pathPreview).build().getUrl(),
                                             request);
  }
}
