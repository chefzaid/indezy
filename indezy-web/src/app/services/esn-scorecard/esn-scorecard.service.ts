import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';

/**
 * An ESN's figures across members: how many freelancers back it and, once the threshold is reached, the median offer rate
 * (percent), process (days), answer time (days), payment delay (days) and the margin's middle half (percent), each null
 * until enough freelancers give it; {@code sharing} whether the reader's records count.
 */
export interface EsnFigures {
  company: string;
  freelancers: number;
  threshold: number;
  offerRate: number | null;
  processDays: number | null;
  marginLow: number | null;
  marginHigh: number | null;
  answerDays: number | null;
  paymentDays: number | null;
  sharing: boolean;
}

/** The ESNs enough freelancers back, the most backed first. */
export interface EsnBoard {
  companies: EsnFigures[];
  threshold: number;
  sharing: boolean;
}

/** The ESN scorecards across members (docs/adr/0016): for the network's members only, so a refusal stays quiet. */
@Injectable({
  providedIn: 'root'
})
export class EsnScorecardService {
  private readonly http = inject(HttpClient);
  private readonly silent = { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) };

  board(): Observable<EsnBoard> {
    return this.http.get<EsnBoard>(`${environment.apiUrl}/network/esn-scorecards`, this.silent);
  }

  /** The workspace's company {@code clientId} across members, matched by its name. */
  company(clientId: number): Observable<EsnFigures> {
    return this.http.get<EsnFigures>(`${environment.apiUrl}/clients/${clientId}/scorecard/members`, this.silent);
  }
}
