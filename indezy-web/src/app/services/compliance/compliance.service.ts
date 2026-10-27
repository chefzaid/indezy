import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ComplianceDetails, ComplianceDocument, CompliancePack, CompliancePackRequest, CreatedCompliancePack, PublicCompliancePack
} from '../../models';

export const PACK_TOKEN_HEADER = 'X-Pack-Token';

/** The compliance vault: documents with their dates, the links that share them, and what a link shows without an account. */
@Injectable({
  providedIn: 'root'
})
export class ComplianceService {
  private readonly API_URL = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<ComplianceDocument[]> {
    return this.http.get<ComplianceDocument[]>(`${this.API_URL}/compliance-documents`);
  }

  upload(file: File, details: ComplianceDetails): Observable<ComplianceDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('type', details.type);
    for (const key of ['label', 'issuedOn', 'expiresOn'] as const) {
      const value = details[key];
      if (value) {
        form.append(key, value);
      }
    }
    return this.http.post<ComplianceDocument>(`${this.API_URL}/compliance-documents`, form);
  }

  update(id: number, details: ComplianceDetails): Observable<ComplianceDocument> {
    return this.http.put<ComplianceDocument>(`${this.API_URL}/compliance-documents/${id}`, details);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/compliance-documents/${id}`);
  }

  content(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/compliance-documents/${id}/content`, { responseType: 'blob' });
  }

  packs(): Observable<CompliancePack[]> {
    return this.http.get<CompliancePack[]>(`${this.API_URL}/compliance-packs`);
  }

  share(request: CompliancePackRequest): Observable<CreatedCompliancePack> {
    return this.http.post<CreatedCompliancePack>(`${this.API_URL}/compliance-packs`, request);
  }

  revoke(id: number): Observable<CompliancePack> {
    return this.http.post<CompliancePack>(`${this.API_URL}/compliance-packs/${id}/revoke`, null);
  }

  publicPack(token: string): Observable<PublicCompliancePack> {
    return this.http.get<PublicCompliancePack>(`${this.API_URL}/public/compliance-packs`, { headers: linkHeaders(token) });
  }

  publicDocument(token: string, id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/public/compliance-packs/documents/${id}`, { headers: linkHeaders(token), responseType: 'blob' });
  }
}

function linkHeaders(token: string): HttpHeaders {
  return new HttpHeaders({ [PACK_TOKEN_HEADER]: token });
}
