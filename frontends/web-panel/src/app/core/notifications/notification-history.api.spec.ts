import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { describe, beforeEach, it, expect, afterEach } from 'vitest';
import { NotificationHistoryApi } from './notification-history.api';

describe('NotificationHistoryApi', () => {
  let api: NotificationHistoryApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        NotificationHistoryApi,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(NotificationHistoryApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list парсит PagedModel HATEOAS', async () => {
    const promise = firstValueFrom(api.list(0, 20, false));

    const req = httpMock.expectOne({
      method: 'GET',
      url: '/api/notifications?page=0&size=20&unreadOnly=false&sort=sentAt,desc',
    });
    req.flush({
      _embedded: {
        notificationHistoryDtoList: [
          {
            id: 'doc-1',
            userId: 42,
            type: 'EXCUSE_APPROVED',
            payload: { group_id: 7 },
            sentAt: '2026-04-24T12:00:00Z',
            readAt: null,
          },
        ],
      },
      page: { size: 20, totalElements: 1, totalPages: 1, number: 0 },
    });

    const page = await promise;
    expect(page.items).toHaveLength(1);
    expect(page.items[0].id).toBe('doc-1');
    expect(page.totalElements).toBe(1);
    expect(page.totalPages).toBe(1);
    expect(page.pageNumber).toBe(0);
  });

  it('list empty _embedded → items=[]', async () => {
    const promise = firstValueFrom(api.list(0, 20, true));
    const req = httpMock.expectOne({
      method: 'GET',
      url: '/api/notifications?page=0&size=20&unreadOnly=true&sort=sentAt,desc',
    });
    req.flush({ page: { size: 20, totalElements: 0, totalPages: 0, number: 0 } });

    const page = await promise;
    expect(page.items).toEqual([]);
    expect(page.totalElements).toBe(0);
  });

  it('unreadCount извлекает .count', async () => {
    const promise = firstValueFrom(api.unreadCount());
    const req = httpMock.expectOne('/api/notifications/unread-count');
    expect(req.request.method).toBe('GET');
    req.flush({ count: 5 });

    await expect(promise).resolves.toBe(5);
  });

  it('markRead url-кодирует id', async () => {
    const promise = firstValueFrom(api.markRead('abc/special?x'));
    const req = httpMock.expectOne('/api/notifications/abc%2Fspecial%3Fx/read');
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
    await promise;
  });

  it('markAllRead делает POST без body', async () => {
    const promise = firstValueFrom(api.markAllRead());
    const req = httpMock.expectOne('/api/notifications/mark-all-read');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush(null);
    await promise;
  });
});
