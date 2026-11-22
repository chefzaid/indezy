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

  /** Sets the logo of the invoices and quotes to come (a PNG or JPEG of 2 MB at most). */
  uploadLogo(file: File): Observable<BillingIdentity> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<BillingIdentity>(`${this.API_URL}/logo`, form);
  }

  /** Sets the terms and conditions PDF appended to the quotes to come (5 MB and 20 pages at most). */
  uploadTermsFile(file: File): Observable<BillingIdentity> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<BillingIdentity>(`${this.API_URL}/quote-terms-file`, form);
  }

  removeTermsFile(): Observable<BillingIdentity> {
    return this.http.delete<BillingIdentity>(`${this.API_URL}/quote-terms-file`);
  }

  termsFile(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/quote-terms-files/${id}`, { responseType: 'blob' });
  }

  removeLogo(): Observable<BillingIdentity> {
    return this.http.delete<BillingIdentity>(`${this.API_URL}/logo`);
  }

  /** One of the workspace's logos, to show. */
  logo(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/logos/${id}`, { responseType: 'blob' });
  }
}
