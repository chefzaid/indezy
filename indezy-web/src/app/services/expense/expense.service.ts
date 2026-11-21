import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Expense, ExpenseDraft, ExpenseSummary } from '../../models';

/** The workspace's business expenses and what they change in its net income. */
@Injectable({
  providedIn: 'root'
})
export class ExpenseService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/expenses`;

  list(year: number): Observable<Expense[]> {
    return this.http.get<Expense[]>(this.API_URL, { params: { year } });
  }

  summary(year: number): Observable<ExpenseSummary> {
    return this.http.get<ExpenseSummary>(`${this.API_URL}/summary/${year}`);
  }

  create(draft: ExpenseDraft): Observable<Expense> {
    return this.http.post<Expense>(this.API_URL, draft);
  }

  update(id: number, draft: ExpenseDraft): Observable<Expense> {
    return this.http.put<Expense>(`${this.API_URL}/${id}`, draft);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
