import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { CompanyHealth } from '../../../models/client.models';
import { ClientService } from '../../../services/client/client.service';

/** Warns that a client or an ESN has closed, or is under insolvency proceedings; says nothing otherwise. */
@Component({
  selector: 'app-company-health',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DatePipe, MatIconModule, TranslateModule],
  template: `
    @if (health?.closed || health?.procedure) {
      <p class="warning" role="note" data-company-health>
        <mat-icon aria-hidden="true">report</mat-icon>
        <span>
          @if (health!.closed) {
            {{ 'clients.health.closed' | translate:{ name: name } }}
          }
          @if (health!.procedure) {
            {{ 'clients.health.procedure' | translate:{ name: name, procedure: ('clients.health.procedures.' + health!.procedure | translate) } }}
            @if (health!.procedureOn) {
              {{ 'clients.health.announcedOn' | translate:{ date: (health!.procedureOn | date:'longDate') } }}
            }
          }
        </span>
      </p>
    }
  `,
  styles: `
    .warning {
      display: flex; gap: 8px; align-items: flex-start; margin: 12px 0 0; padding: 8px 12px; border-radius: 8px;
      background: var(--app-info-bg); color: var(--app-text);
    }
    mat-icon { color: var(--app-danger); flex-shrink: 0; }
  `
})
export class CompanyHealthComponent implements OnChanges {
  private readonly clients = inject(ClientService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) clientId!: number;
  @Input() name = '';

  health: CompanyHealth | null = null;

  ngOnChanges(): void {
    this.health = null;
    this.clients.getHealth(this.clientId).subscribe({
      next: health => {
        this.health = health;
        this.changes.markForCheck();
      },
      error: () => {
        this.health = null;
        this.changes.markForCheck();
      }
    });
  }
}
