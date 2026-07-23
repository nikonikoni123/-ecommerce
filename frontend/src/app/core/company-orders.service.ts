import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  CompanyOrderRow,
  CompanyOrderStats,
  OrderDetail,
  PageResponse,
  RefundView,
  StatusOption,
} from './models';

export interface CompanyOrderFilters {
  status?: string;
  /** priority, date, dueDate, units o status. */
  sort?: string;
  onlyOverdue?: boolean;
  page?: number;
  size?: number;
}

/** Panel de pedidos de la empresa: listado, cambios de estado y de productos, y reembolsos. */
@Injectable({ providedIn: 'root' })
export class CompanyOrdersService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/company`;

  list(filters: CompanyOrderFilters = {}): Observable<PageResponse<CompanyOrderRow>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<PageResponse<CompanyOrderRow>>(`${this.base}/orders`, { params });
  }

  stats(): Observable<CompanyOrderStats> {
    return this.http.get<CompanyOrderStats>(`${this.base}/orders/stats`);
  }

  /** Estados y sus transiciones: la interfaz solo ofrece las que el backend aceptaria. */
  statuses(): Observable<StatusOption[]> {
    return this.http.get<StatusOption[]>(`${this.base}/orders/statuses`);
  }

  detail(id: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/orders/${id}`);
  }

  changeStatus(id: string, status: string, note?: string): Observable<OrderDetail> {
    return this.http.patch<OrderDetail>(`${this.base}/orders/${id}/status`, { status, note });
  }

  changeItems(
    id: string,
    items: { productId: string; quantity: number }[],
    note?: string,
  ): Observable<OrderDetail> {
    return this.http.put<OrderDetail>(`${this.base}/orders/${id}/items`, { items, note });
  }

  refunds(status?: string, page = 0, size = 50): Observable<PageResponse<RefundView>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<RefundView>>(`${this.base}/refunds`, { params });
  }

  resolveRefund(id: string, approve: boolean, resolution?: string): Observable<RefundView> {
    return this.http.post<RefundView>(`${this.base}/refunds/${id}/resolve`, {
      approve,
      resolution,
    });
  }
}
