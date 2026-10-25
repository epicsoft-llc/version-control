import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UrlBuilder} from '../item/url.item';
import {TaskListDto} from '../dto/task.dto';
import {UpdateDecisionDto, UpdateDecisionRequest} from '../dto/update-decision.dto';

/** The task list, and the decisions that keep entries off it. */
@Injectable({providedIn: 'root'})
export class TaskRepo {

  private readonly http: HttpClient = inject(HttpClient);

  private readonly pathTasks = '/v1/tasks';
  private readonly pathDecisions = '/v1/update-decisions';

  public getByProject(projectId: string): Observable<TaskListDto> {
    return this.http.get<TaskListDto>(UrlBuilder.builder()
                                                .addPath(this.pathTasks)
                                                .addQuery('projectId', projectId)
                                                .build().getUrl());
  }

  /** No decision yet is an empty list, not an error. */
  public findDecisionsByComponent(componentId: string): Observable<UpdateDecisionDto[]> {
    return this.http.get<UpdateDecisionDto[]>(UrlBuilder.builder()
                                                        .addPath(this.pathDecisions)
                                                        .addQuery('componentId', componentId)
                                                        .build().getUrl());
  }

  /** Every decision in a project, so the matrix can tell a held component from one that is merely behind. */
  public findDecisionsByProject(projectId: string): Observable<UpdateDecisionDto[]> {
    return this.http.get<UpdateDecisionDto[]>(UrlBuilder.builder()
                                                        .addPath(this.pathDecisions)
                                                        .addQuery('projectId', projectId)
                                                        .build().getUrl());
  }

  public decide(request: UpdateDecisionRequest): Observable<UpdateDecisionDto> {
    return this.http.post<UpdateDecisionDto>(UrlBuilder.builder().addPath(this.pathDecisions).build().getUrl(),
                                             request);
  }
}
