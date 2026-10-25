import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UrlBuilder} from '../item/url.item';
import {ComponentDto, ComponentRequest} from '../dto/component.dto';
import {UpgradeProposalDto} from '../dto/upgrade.dto';
import {DependencyCheckDto} from '../dto/dependency.dto';
import {ComponentChangeDto} from '../dto/component-change.dto';

@Injectable({providedIn: 'root'})
export class ComponentRepo {

  private readonly http: HttpClient = inject(HttpClient);

  private readonly pathAll = '/v1/components';
  private readonly pathOne = '/v1/components/{id}';
  private readonly pathProposals = '/v1/components/{id}/proposals';
  private readonly pathDependencies = '/v1/components/{id}/dependencies';
  private readonly pathChanges = '/v1/components/{id}/changes';

  public findAllByProject(projectId: string): Observable<ComponentDto[]> {
    return this.http.get<ComponentDto[]>(UrlBuilder.builder()
                                                   .addPath(this.pathAll)
                                                   .addQuery('projectId', projectId)
                                                   .build().getUrl());
  }

  public getById(id: string): Observable<ComponentDto> {
    return this.http.get<ComponentDto>(UrlBuilder.builder().addPath(this.pathOne).addParam('id', id).build().getUrl());
  }

  /** What to do next in each environment, with the versions that cannot be skipped. */
  public getProposals(id: string): Observable<UpgradeProposalDto[]> {
    return this.http.get<UpgradeProposalDto[]>(UrlBuilder.builder()
                                                         .addPath(this.pathProposals)
                                                         .addParam('id', id)
                                                         .build().getUrl());
  }

  /**
   * Both directions: what this component needs, and what needs it. The second is
   * what says whether an update would break something.
   */
  public getDependencies(id: string, dependents: boolean): Observable<DependencyCheckDto[]> {
    return this.http.get<DependencyCheckDto[]>(UrlBuilder.builder()
                                                         .addPath(this.pathDependencies)
                                                         .addParam('id', id)
                                                         .addQuery('dependents', dependents)
                                                         .build().getUrl());
  }

  /** Master data, decisions and rollout plans, newest first; version changes have their own history. */
  public getChanges(id: string): Observable<ComponentChangeDto[]> {
    return this.http.get<ComponentChangeDto[]>(UrlBuilder.builder()
                                                         .addPath(this.pathChanges)
                                                         .addParam('id', id)
                                                         .build().getUrl());
  }

  public create(request: ComponentRequest): Observable<ComponentDto> {
    return this.http.post<ComponentDto>(UrlBuilder.builder().addPath(this.pathAll).build().getUrl(), request);
  }

  public update(id: string, request: ComponentRequest): Observable<ComponentDto> {
    return this.http.put<ComponentDto>(UrlBuilder.builder().addPath(this.pathOne).addParam('id', id).build().getUrl(),
                                       request);
  }

  public delete(id: string): Observable<void> {
    return this.http.delete<void>(UrlBuilder.builder().addPath(this.pathOne).addParam('id', id).build().getUrl());
  }
}
