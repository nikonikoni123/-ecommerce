import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import {
  CartView,
  CheckoutRequest,
  CheckoutResponse,
  MessageResponse,
  OrderDetail,
  OrderSummary,
  PageResponse,
  RefundView,
  SurpriseProposal,
} from './models';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiBaseUrl}/cart`;

  /** Numero de articulos, para el indicador del encabezado. */
  private readonly units = signal(0);
  readonly itemCount = this.units.asReadonly();
  readonly hasItems = computed(() => this.units() > 0);

  /** Refresca el contador. Solo tiene sentido para las cuentas de comprador. */
  refreshCount(): void {
    if (!this.auth.isCustomer()) {
      this.units.set(0);
      return;
    }
    this.http.get<{ count: number }>(`${this.base}/count`).subscribe({
      next: (r) => this.units.set(r.count),
      error: () => this.units.set(0),
    });
  }

  view(): Observable<CartView> {
    return this.http.get<CartView>(this.base).pipe(tap((c) => this.units.set(c.totalUnits)));
  }

  add(productId: string, quantity = 1): Observable<CartView> {
    return this.http
      .post<CartView>(`${this.base}/items`, { productId, quantity })
      .pipe(tap((c) => this.units.set(c.totalUnits)));
  }

  /**
   * Anade varios productos en una sola peticion. Enviarlos en paralelo seria una condicion de
   * carrera: cada peticion lee el carrito, anade lo suyo y guarda, y solo sobrevive la ultima.
   */
  addMany(
    items: { productId: string; quantity: number }[],
    asRandomOrder = false,
  ): Observable<CartView> {
    return this.http
      .post<CartView>(`${this.base}/items/bulk`, { items, asRandomOrder })
      .pipe(tap((c) => this.units.set(c.totalUnits)));
  }

  updateQuantity(productId: string, quantity: number): Observable<CartView> {
    return this.http
      .patch<CartView>(`${this.base}/items/${productId}`, { quantity })
      .pipe(tap((c) => this.units.set(c.totalUnits)));
  }

  replace(productId: string, newProductId: string, quantity: number): Observable<CartView> {
    return this.http
      .put<CartView>(`${this.base}/items/${productId}`, { newProductId, quantity })
      .pipe(tap((c) => this.units.set(c.totalUnits)));
  }

  remove(productId: string): Observable<CartView> {
    return this.http
      .delete<CartView>(`${this.base}/items/${productId}`)
      .pipe(tap((c) => this.units.set(c.totalUnits)));
  }

  clear(): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(this.base).pipe(tap(() => this.units.set(0)));
  }
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly cart = inject(CartService);
  private readonly base = environment.apiBaseUrl;

  checkout(payload: CheckoutRequest): Observable<CheckoutResponse> {
    // El carrito queda vacio en el servidor: se refleja de inmediato en el indicador.
    return this.http
      .post<CheckoutResponse>(`${this.base}/checkout`, payload)
      .pipe(tap(() => this.cart.refreshCount()));
  }

  surprise(items: number, maxTotal?: number, category?: string): Observable<SurpriseProposal> {
    return this.http.post<SurpriseProposal>(`${this.base}/orders/surprise/preview`, {
      items,
      maxTotal: maxTotal ?? null,
      category: category ?? null,
    });
  }

  list(scope?: 'active' | 'history', page = 0, size = 10): Observable<PageResponse<OrderSummary>> {
    const params: Record<string, string | number> = { page, size };
    if (scope) {
      params['scope'] = scope;
    }
    return this.http.get<PageResponse<OrderSummary>>(`${this.base}/orders`, { params });
  }

  detail(id: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/orders/${id}`);
  }

  /** Descarga la factura como blob, para poder forzar el guardado con el nombre correcto. */
  invoice(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/orders/${id}/invoice`, { responseType: 'blob' });
  }

  requestRefund(orderId: string, reason: string): Observable<RefundView> {
    return this.http.post<RefundView>(`${this.base}/orders/${orderId}/refund`, { reason });
  }

  myRefunds(): Observable<RefundView[]> {
    return this.http.get<RefundView[]>(`${this.base}/refunds`);
  }
}
