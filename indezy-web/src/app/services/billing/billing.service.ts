import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BillingIdentity, DocumentTemplate, DocumentTemplateKind } from '../../models';

/** The seller's identity on the signed-in workspace's invoices. */
/** A built-in layout of the documents. */
export type DocumentLayout = 'SIMPLE' | 'MODERN' | 'CLASSIC';

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

  /** Prints the documents to come in an uploaded TrueType font, with its bold face when given. */
  uploadFont(regular: File, bold: File | null): Observable<BillingIdentity> {
    const form = new FormData();
    form.append('file', regular);
    if (bold) {
      form.append('bold', bold);
    }
    return this.http.post<BillingIdentity>(`${this.API_URL}/font`, form);
  }

  removeFont(): Observable<BillingIdentity> {
    return this.http.delete<BillingIdentity>(`${this.API_URL}/font`);
  }

  templates(): Observable<DocumentTemplate[]> {
    return this.http.get<DocumentTemplate[]>(`${this.API_URL}/templates`);
  }

  placeholders(): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/templates/placeholders`);
  }

  /** Sets the Word template of the invoices or of the quotes (a .docx of 5 MB at most, without macros). */
  uploadTemplate(kind: DocumentTemplateKind, file: File): Observable<DocumentTemplate> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<DocumentTemplate>(`${this.API_URL}/templates/${kind}`, form);
  }

  /**
   * A Word template to start from: a built-in layout (the documents' own without one) with every placeholder in place,
   * labels in the language given.
   */
  starterTemplate(kind: DocumentTemplateKind, language: 'FR' | 'EN', theme?: DocumentLayout): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/users/billing/templates/${kind}/starter`,
      { params: theme ? { language, theme } : { language }, responseType: 'blob' });
  }

  /** The template to start from, filled with a sample document numbered and dated today. */
  starterExample(kind: DocumentTemplateKind, language: 'FR' | 'EN'): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/users/billing/templates/${kind}/example`, { params: { language }, responseType: 'blob' });
  }

  /** The template filled with the latest invoice or quote, or a sample one before the first. */
  templatePreview(kind: DocumentTemplateKind): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/${kind === 'INVOICE' ? 'invoices' : 'quotes'}/template-preview`, { responseType: 'blob' });
  }

  removeTemplate(kind: DocumentTemplateKind): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/templates/${kind}`);
  }

  /** Draws the documents to come over a letterhead (a PDF of one or two A4 pages, its fonts embedded). */
  uploadLetterhead(file: File): Observable<BillingIdentity> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<BillingIdentity>(`${this.API_URL}/letterhead`, form);
  }

  removeLetterhead(): Observable<BillingIdentity> {
    return this.http.delete<BillingIdentity>(`${this.API_URL}/letterhead`);
  }

  letterhead(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/letterheads/${id}`, { responseType: 'blob' });
  }

  /** The first page of an invoice in the style of these settings, saved or not, as an image. */
  stylePreview(identity: BillingIdentity, quote = false): Observable<Blob> {
    return this.http.post(`${environment.apiUrl}/invoices/style-preview${quote ? '?quote=true' : ''}`, identity, { responseType: 'blob' });
  }

  removeLogo(): Observable<BillingIdentity> {
    return this.http.delete<BillingIdentity>(`${this.API_URL}/logo`);
  }

  /** One of the workspace's logos, to show. */
  logo(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/logos/${id}`, { responseType: 'blob' });
  }
}
