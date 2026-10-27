import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PurchaseOrder, PurchaseOrderWrite } from '../../models';

/** A mission's purchase orders and amendments; each write answers with the order as it now stands. */
@Injectable({
  providedIn: 'root'
})
export class PurchaseOrderService {
  private readonly API_URL = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  list(projectId: number): Observable<PurchaseOrder[]> {
    return this.http.get<PurchaseOrder[]>(`${this.API_URL}/projects/${projectId}/purchase-orders`);
  }

  create(projectId: number, write: PurchaseOrderWrite): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${this.API_URL}/projects/${projectId}/purchase-orders`, write);
  }

  update(id: number, write: PurchaseOrderWrite): Observable<PurchaseOrder> {
    return this.http.put<PurchaseOrder>(`${this.API_URL}/purchase-orders/${id}`, write);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/purchase-orders/${id}`);
  }
}
