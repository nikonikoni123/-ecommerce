import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ActivityRow,
  Dashboard,
  DepartmentView,
  GoalRequest,
  GoalView,
  MemberView,
  MessageResponse,
  MetricOption,
  PageResponse,
  PermissionView,
  RoleView,
  TargetScope,
} from './models';

/** Peticiones para crear o modificar un cargo. */
export interface RolePayload {
  name: string;
  description: string;
  permissions: string[];
}

/** Alta de un usuario de la empresa. */
export interface CreateMemberPayload {
  name: string;
  email: string;
  position?: string | null;
  departmentId?: string | null;
  managerId?: string | null;
  roleIds: string[];
}

/** Modificacion parcial de un usuario: solo se envian los campos presentes. */
export interface UpdateMemberPayload {
  name?: string;
  position?: string | null;
  departmentId?: string | null;
  managerId?: string | null;
  roleIds?: string[];
  extraPermissions?: string[];
  revokedPermissions?: string[];
}

export interface DepartmentPayload {
  name: string;
  description: string;
  leaderUserId?: string | null;
}

/**
 * Administracion de la empresa por root: catalogo de permisos, cargos, usuarios, departamentos y el
 * visor de actividad. Cada bloque exige su permiso en el backend; aqui solo se hacen las llamadas.
 */
@Injectable({ providedIn: 'root' })
export class CompanyAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/company/admin`;

  // -------------------------------------------------------------- permisos
  permissions(): Observable<PermissionView[]> {
    return this.http.get<PermissionView[]>(`${this.base}/permissions`);
  }

  // -------------------------------------------------------------- cargos
  roles(): Observable<RoleView[]> {
    return this.http.get<RoleView[]>(`${this.base}/roles`);
  }

  createRole(payload: RolePayload): Observable<RoleView> {
    return this.http.post<RoleView>(`${this.base}/roles`, payload);
  }

  updateRole(id: string, payload: RolePayload): Observable<RoleView> {
    return this.http.put<RoleView>(`${this.base}/roles/${id}`, payload);
  }

  deleteRole(id: string): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(`${this.base}/roles/${id}`);
  }

  // -------------------------------------------------------------- usuarios
  members(): Observable<MemberView[]> {
    return this.http.get<MemberView[]>(`${this.base}/members`);
  }

  createMember(payload: CreateMemberPayload): Observable<MemberView> {
    return this.http.post<MemberView>(`${this.base}/members`, payload);
  }

  updateMember(id: string, payload: UpdateMemberPayload): Observable<MemberView> {
    return this.http.patch<MemberView>(`${this.base}/members/${id}`, payload);
  }

  deleteMember(id: string): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(`${this.base}/members/${id}`);
  }

  // -------------------------------------------------------------- departamentos
  departments(): Observable<DepartmentView[]> {
    return this.http.get<DepartmentView[]>(`${this.base}/departments`);
  }

  createDepartment(payload: DepartmentPayload): Observable<DepartmentView> {
    return this.http.post<DepartmentView>(`${this.base}/departments`, payload);
  }

  updateDepartment(id: string, payload: DepartmentPayload): Observable<DepartmentView> {
    return this.http.put<DepartmentView>(`${this.base}/departments/${id}`, payload);
  }

  deleteDepartment(id: string): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(`${this.base}/departments/${id}`);
  }

  // -------------------------------------------------------------- actividad
  activity(userId?: string, page = 0, size = 40): Observable<PageResponse<ActivityRow>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (userId) params = params.set('userId', userId);
    return this.http.get<PageResponse<ActivityRow>>(`${this.base}/activity`, { params });
  }
}

/** Metas KPI y panel de resultados. */
@Injectable({ providedIn: 'root' })
export class KpiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/company/kpi`;

  metrics(): Observable<MetricOption[]> {
    return this.http.get<MetricOption[]>(`${this.base}/metrics`);
  }

  goals(): Observable<GoalView[]> {
    return this.http.get<GoalView[]>(`${this.base}/goals`);
  }

  targets(): Observable<TargetScope> {
    return this.http.get<TargetScope>(`${this.base}/targets`);
  }

  createGoal(payload: GoalRequest): Observable<GoalView> {
    return this.http.post<GoalView>(`${this.base}/goals`, payload);
  }

  updateGoal(id: string, payload: GoalRequest): Observable<GoalView> {
    return this.http.put<GoalView>(`${this.base}/goals/${id}`, payload);
  }

  deleteGoal(id: string): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(`${this.base}/goals/${id}`);
  }

  dashboard(months = 6): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/dashboard`, {
      params: new HttpParams().set('months', months),
    });
  }
}
