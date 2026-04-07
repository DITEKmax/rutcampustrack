import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import type {
  AssignmentResponse,
  GroupResponse,
  JournalResponse,
  PagedResponse,
  SubjectResponse,
} from './types';

@Injectable({ providedIn: 'root' })
export class JournalApiService {
  private readonly http = inject(HttpClient);

  getJournal(
    groupId: number,
    subjectId: number,
    dateFrom: string,
    dateTo: string,
  ): Observable<JournalResponse> {
    const params = new HttpParams()
      .set('groupId', groupId)
      .set('subjectId', subjectId)
      .set('dateFrom', dateFrom)
      .set('dateTo', dateTo);
    return this.http.get<JournalResponse>('/api/attendance/reports/journal', { params });
  }

  getMyAssignments(): Observable<AssignmentResponse[]> {
    return this.http
      .get<PagedResponse<AssignmentResponse>>('/api/academic/assignments/my')
      .pipe(
        map(response => (Object.values(response._embedded ?? {})[0] ?? []) as AssignmentResponse[]),
      );
  }

  getGroups(): Observable<GroupResponse[]> {
    return this.http
      .get<PagedResponse<GroupResponse>>('/api/academic/groups', {
        params: new HttpParams().set('size', '200'),
      })
      .pipe(
        map(response => (Object.values(response._embedded ?? {})[0] ?? []) as GroupResponse[]),
      );
  }

  getSubjects(): Observable<SubjectResponse[]> {
    return this.http
      .get<PagedResponse<SubjectResponse>>('/api/academic/subjects', {
        params: new HttpParams().set('size', '200'),
      })
      .pipe(
        map(response => (Object.values(response._embedded ?? {})[0] ?? []) as SubjectResponse[]),
      );
  }
}
