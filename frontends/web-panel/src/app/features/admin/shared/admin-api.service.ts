import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import type {
  UserResponse,
  UserCreatedResponse,
  CreateUserRequest,
  PatchUserRequest,
  GroupResponse,
  CreateGroupRequest,
  UpdateGroupRequest,
  SemesterResponse,
  CreateSemesterRequest,
  UpdateSemesterRequest,
  DashboardStatsResponse,
  PagedResponse,
} from './types';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  listUsers(params: {
    page?: number;
    size?: number;
    role?: string;
    status?: string;
    search?: string;
  }): Observable<{ items: UserResponse[]; total: number }> {
    let httpParams = new HttpParams();
    if (params.page != null) httpParams = httpParams.set('page', params.page);
    if (params.size != null) httpParams = httpParams.set('size', params.size);
    if (params.role) httpParams = httpParams.set('role', params.role.toUpperCase());
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.search) httpParams = httpParams.set('search', params.search);

    return this.http
      .get<PagedResponse<UserResponse>>('/api/academic/users', { params: httpParams })
      .pipe(
        map(res => ({
          items: (Object.values(res._embedded ?? {})[0] ?? []) as UserResponse[],
          total: res.page.totalElements,
        })),
      );
  }

  createUser(req: CreateUserRequest): Observable<UserCreatedResponse> {
    return this.http.post<UserCreatedResponse>('/api/academic/users', req);
  }

  getUser(id: number): Observable<UserResponse> {
    return this.http.get<UserResponse>(`/api/academic/users/${id}`);
  }

  patchUser(id: number, req: PatchUserRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`/api/academic/users/${id}`, req);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`/api/academic/users/${id}`);
  }

  listGroups(): Observable<GroupResponse[]> {
    return this.http
      .get<PagedResponse<GroupResponse>>('/api/academic/groups', {
        params: new HttpParams().set('size', '200'),
      })
      .pipe(
        map(res => (Object.values(res._embedded ?? {})[0] ?? []) as GroupResponse[]),
      );
  }

  createGroup(req: CreateGroupRequest): Observable<GroupResponse> {
    return this.http.post<GroupResponse>('/api/academic/groups', req);
  }

  updateGroup(id: number, req: UpdateGroupRequest): Observable<GroupResponse> {
    return this.http.put<GroupResponse>(`/api/academic/groups/${id}`, req);
  }

  deleteGroup(id: number): Observable<void> {
    return this.http.delete<void>(`/api/academic/groups/${id}`);
  }

  listSemesters(): Observable<SemesterResponse[]> {
    return this.http
      .get<PagedResponse<SemesterResponse>>('/api/academic/semesters', {
        params: new HttpParams().set('size', '200'),
      })
      .pipe(
        map(res => (Object.values(res._embedded ?? {})[0] ?? []) as SemesterResponse[]),
      );
  }

  createSemester(req: CreateSemesterRequest): Observable<SemesterResponse> {
    return this.http.post<SemesterResponse>('/api/academic/semesters', req);
  }

  updateSemester(id: number, req: UpdateSemesterRequest): Observable<SemesterResponse> {
    return this.http.put<SemesterResponse>(`/api/academic/semesters/${id}`, req);
  }

  deleteSemester(id: number, confirmation: string): Observable<void> {
    return this.http.request<void>('DELETE', `/api/academic/semesters/${id}`, {
      body: { confirmation },
    });
  }

  activateSemester(id: number): Observable<void> {
    return this.http.patch<void>(`/api/academic/semesters/${id}/activate`, null);
  }

  /**
   * BUG-006-7 / plan 58-05: проверка пересечения дат семестра.
   * Используется async-валидатором semester-dialog до submit.
   */
  checkSemesterOverlap(
    from: string,
    to: string,
    excludeId?: number,
  ): Observable<{ overlaps: boolean; conflictingName?: string | null }> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (excludeId != null) params = params.set('excludeId', excludeId);
    return this.http.get<{ overlaps: boolean; conflictingName?: string | null }>(
      '/api/academic/semesters/overlap',
      { params },
    );
  }

  getDashboardStats(): Observable<DashboardStatsResponse> {
    return this.http.get<DashboardStatsResponse>('/api/academic/dashboard/stats');
  }

  listStudentsByGroup(groupId: number): Observable<UserResponse[]> {
    return this.http
      .get<PagedResponse<UserResponse>>('/api/academic/users', {
        params: new HttpParams().set('role', 'STUDENT').set('size', '500'),
      })
      .pipe(
        map(res => (Object.values(res._embedded ?? {})[0] ?? []) as UserResponse[]),
        map(users => users.filter(u => u.groupId === groupId)),
      );
  }
}
