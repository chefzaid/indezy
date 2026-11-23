import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { concatMap, from, toArray } from 'rxjs';

import { BankMatch, Invoice } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';

/** A statement this large at most is read (the server takes 2 MB). */
export const STATEMENT_MAX_BYTES = 2_000_000;

/**
 * Matches a bank statement, the CSV the bank exports, with the invoices awaiting payment: each credit with the invoice
 * it pays and why, the matches ticked, the others listed; **Mark N invoices paid** records each kept one paid on its
 * credit's day, then closes with the invoices updated.
 */
@Component({
  selector: 'app-invoice-bank-dialog',
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatCheckboxModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'invoices.bank.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="intro">{{ 'invoices.bank.intro' | translate }}</p>
      <label class="file">
        <span>{{ 'invoices.bank.file' | translate }}</span>
        <input type="file" accept=".csv,text/csv,text/plain" (change)="read($event)" data-bank-file>
      </label>
      @if (reading) {
        <mat-spinner diameter="24" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
      }
      @if (error) {
        <p class="error" role="alert" data-bank-error>{{ error | translate }}</p>
      }
      @if (matches) {
        @if (!matches.length) {
          <p data-bank-none>{{ 'invoices.bank.none' | translate }}</p>
        } @else {
          <p role="status" data-bank-summary>{{ 'invoices.bank.summary' | translate:{ credits: matches.length, matched: matched().length } }}</p>
          <ul class="credits">
            @for (match of matches; track $index) {
              <li [class.unmatched]="!match.invoiceId" [attr.data-bank-credit]="$index">
                @if (match.invoiceId) {
                  <mat-checkbox [checked]="kept.has($index)" (change)="toggle($index)" [attr.data-bank-keep]="match.invoiceId">
                    <span class="invoice">{{ match.invoiceNumber }} · {{ match.clientName }}</span>
                  </mat-checkbox>
                  <span class="reason">{{ 'invoices.bank.reasons.' + match.reason | translate }}</span>
                } @else {
                  <span class="invoice">{{ 'invoices.bank.unmatched' | translate }}</span>
                }
                <span class="credit">{{ match.date | date:'mediumDate' }} · {{ match.amount | number:'1.2-2' }} € · {{ match.label }}</span>
              </li>
            }
          </ul>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="updated">{{ 'common.close' | translate }}</button>
      <button mat-flat-button color="primary" type="button" [disabled]="!kept.size || saving" (click)="markPaid()" data-bank-mark-paid>
        {{ 'invoices.bank.markPaid' | translate:{ count: kept.size } }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .intro, .reason, .credit { color: var(--app-text-muted); }
    .file { display: flex; flex-direction: column; gap: 4px; margin: 8px 0 12px; }
    .file input { max-width: 100%; }
    .error { color: var(--app-danger); }
    .credits { list-style: none; margin: 0; padding: 0; }
    .credits li { display: flex; flex-direction: column; gap: 2px; padding: 8px 0;
      border-bottom: 1px solid var(--app-border, rgba(0, 0, 0, 0.12)); }
    .credits li:last-child { border-bottom: none; }
    .unmatched .invoice { color: var(--app-text-muted); }
    .reason, .credit { font-size: 0.875rem; overflow-wrap: anywhere; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InvoiceBankDialogComponent {
  private readonly invoices = inject(InvoiceService);
  private readonly dialog = inject(MatDialogRef<InvoiceBankDialogComponent, Invoice[]>);
  private readonly changes = inject(ChangeDetectorRef);

  matches: BankMatch[] | null = null;
  /** The indexes of the matches ticked. */
  readonly kept = new Set<number>();
  /** The invoices marked paid so far, handed back on closing. */
  updated: Invoice[] = [];
  reading = false;
  saving = false;
  error: string | null = null;

  matched(): BankMatch[] {
    return (this.matches ?? []).filter(match => match.invoiceId);
  }

  /** Reads the chosen file and matches its credits, the matches ticked. */
  read(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) {
      return;
    }
    this.error = null;
    this.matches = null;
    this.kept.clear();
    if (file.size > STATEMENT_MAX_BYTES) {
      this.error = 'invoices.bank.tooLarge';
      return;
    }
    this.reading = true;
    file.text().then(content => this.match(content), () => this.failed('invoices.bank.readError'));
  }

  match(content: string): void {
    this.reading = true;
    this.invoices.reconcile(content).subscribe({
      next: matches => {
        this.reading = false;
        this.matches = matches;
        matches.forEach((match, index) => match.invoiceId && this.kept.add(index));
        this.changes.markForCheck();
      },
      error: () => this.failed('invoices.bank.readError')
    });
  }

  toggle(index: number): void {
    if (this.kept.has(index)) {
      this.kept.delete(index);
    } else {
      this.kept.add(index);
    }
  }

  /** Marks each ticked invoice paid on its credit's day, one after the other, then closes with them. */
  markPaid(): void {
    const chosen = [...this.kept].map(index => this.matches![index]);
    this.saving = true;
    from(chosen).pipe(concatMap(match => this.invoices.setPaid(match.invoiceId!, match.date)), toArray()).subscribe({
      next: paid => {
        this.saving = false;
        this.updated = [...this.updated, ...paid];
        this.dialog.close(this.updated);
      },
      error: () => {
        this.saving = false;
        this.failed('invoices.bank.saveError');
      }
    });
  }

  private failed(key: string): void {
    this.reading = false;
    this.error = key;
    this.changes.markForCheck();
  }
}
