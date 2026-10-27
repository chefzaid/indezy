import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BillingIdentity } from '../../models';

/** The seller's identity on the signed-in workspace's invoices. */
@Injectable({
  providedIn: 'root'
})
export class BillingService {
  private readonly API_URL = `${environment.apiUrl}/users/billing`;

  constructor(private readonly http: HttpClient) {}

  identity(): Observable<BillingIdentity> {
    return this.http.get<BillingIdentity>(this.API_URL);
  }

  saveIdentity(identity: BillingIdentity): Observable<BillingIdentity> {
    return this.http.put<BillingIdentity>(this.API_URL, identity);
  }
}
