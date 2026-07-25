import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CompanyProduct, MessageResponse, PageResponse, ProductDetail, ProductSummary } from './models';

export interface CatalogFilters {
  search?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  inStockOnly?: boolean;
  sort?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  browse(filters: CatalogFilters = {}): Observable<PageResponse<ProductSummary>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      // Se omiten los filtros vacios para no ensuciar la URL ni la consulta del backend.
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<PageResponse<ProductSummary>>(`${this.base}/products`, { params });
  }

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/products/categories`);
  }

  detail(slug: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${this.base}/products/${slug}`);
  }
}

@Injectable({ providedIn: 'root' })
export class CompanyCatalogService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/company/products`;

  list(page: number, size: number): Observable<PageResponse<CompanyProduct>> {
    return this.http.get<PageResponse<CompanyProduct>>(this.base, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  get(id: string): Observable<CompanyProduct> {
    return this.http.get<CompanyProduct>(`${this.base}/${id}`);
  }

  create(payload: unknown): Observable<CompanyProduct> {
    return this.http.post<CompanyProduct>(this.base, payload);
  }

  update(id: string, payload: unknown): Observable<CompanyProduct> {
    return this.http.patch<CompanyProduct>(`${this.base}/${id}`, payload);
  }

  updateStock(id: string, stock: number): Observable<CompanyProduct> {
    return this.http.patch<CompanyProduct>(`${this.base}/${id}/stock`, { stock });
  }

  remove(id: string): Observable<MessageResponse> {
    return this.http.delete<MessageResponse>(`${this.base}/${id}`);
  }
}
