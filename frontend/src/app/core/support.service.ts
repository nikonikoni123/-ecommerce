import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CaseDetail,
  CaseRow,
  CaseStats,
  CaseStatusOption,
  ChatFeed,
  ChatMessageView,
  DepartmentChannel,
  PageResponse,
} from './models';

/** Casos de atencion del cliente: abrir, listar, ver el hilo y responder. */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/cases`;

  open(subject: string, message: string, orderId: string): Observable<CaseDetail> {
    return this.http.post<CaseDetail>(this.base, { subject, message, orderId });
  }

  myCases(page = 0, size = 20): Observable<PageResponse<CaseRow>> {
    return this.http.get<PageResponse<CaseRow>>(this.base, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  detail(id: string): Observable<CaseDetail> {
    return this.http.get<CaseDetail>(`${this.base}/${id}`);
  }

  reply(id: string, message: string): Observable<CaseDetail> {
    return this.http.post<CaseDetail>(`${this.base}/${id}/messages`, { message });
  }
}

/** Pestana de atencion a casos de la empresa. */
@Injectable({ providedIn: 'root' })
export class CompanySupportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/company/cases`;

  inbox(status?: string, sort?: string, page = 0, size = 50): Observable<PageResponse<CaseRow>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (sort) params = params.set('sort', sort);
    return this.http.get<PageResponse<CaseRow>>(this.base, { params });
  }

  stats(): Observable<CaseStats> {
    return this.http.get<CaseStats>(`${this.base}/stats`);
  }

  statuses(): Observable<CaseStatusOption[]> {
    return this.http.get<CaseStatusOption[]>(`${this.base}/statuses`);
  }

  detail(id: string): Observable<CaseDetail> {
    return this.http.get<CaseDetail>(`${this.base}/${id}`);
  }

  assign(id: string, agentUserId?: string): Observable<CaseDetail> {
    return this.http.post<CaseDetail>(`${this.base}/${id}/assign`, { agentUserId: agentUserId ?? null });
  }

  reply(id: string, message: string): Observable<CaseDetail> {
    return this.http.post<CaseDetail>(`${this.base}/${id}/reply`, { message });
  }

  changeStatus(id: string, status: string): Observable<CaseDetail> {
    return this.http.patch<CaseDetail>(`${this.base}/${id}/status`, { status });
  }
}

/** Chat general de la empresa y comunicados de root. */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/company/chat`;

  feed(page = 0, size = 40): Observable<ChatFeed> {
    return this.http.get<ChatFeed>(this.base, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  post(body: string): Observable<ChatMessageView> {
    return this.http.post<ChatMessageView>(this.base, { body });
  }

  broadcast(body: string): Observable<ChatMessageView> {
    return this.http.post<ChatMessageView>(`${this.base}/broadcast`, { body });
  }

  // -------------------------------------------------------------- chat por departamento

  /** Departamentos a cuyo chat de equipo tiene acceso quien consulta. */
  departments(): Observable<DepartmentChannel[]> {
    return this.http.get<DepartmentChannel[]>(`${this.base}/departments`);
  }

  departmentFeed(id: string, page = 0, size = 40): Observable<PageResponse<ChatMessageView>> {
    return this.http.get<PageResponse<ChatMessageView>>(`${this.base}/departments/${id}`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  postToDepartment(id: string, body: string): Observable<ChatMessageView> {
    return this.http.post<ChatMessageView>(`${this.base}/departments/${id}`, { body });
  }
}
