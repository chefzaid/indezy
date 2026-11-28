import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { CvAudit, CvAuditFinding, CvProfile } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';

/**
 * A CV profile's dossier checked before it goes out (see docs/features.md#cv-profiles), named or anonymized, without AI:
 * the names an anonymized one still gives first, then what weakens it, its length.
 */
@Component({
  selector: 'app-cv-audit-dialog',
  imports: [FormsModule, MatButtonModule, MatButtonToggleModule, MatDialogModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'cvAudit.title' | translate:{ name: profile.name } }}</h2>
    <mat-dialog-content>
      <mat-button-toggle-group [(ngModel)]="layout" (change)="load()" [attr.aria-label]="'cvShares.layout' | translate" data-cv-audit-layout>
        <mat-button-toggle value="ANONYMOUS">{{ 'cvShares.anonymous' | translate }}</mat-button-toggle>
        <mat-button-toggle value="NAMED">{{ 'cvShares.named' | translate }}</mat-button-toggle>
      </mat-button-toggle-group>
      @if (!audit) {
        <div class="busy"><mat-spinner diameter="28" [attr.aria-label]="'common.loading' | translate"></mat-spinner></div>
      } @else {
        <p class="pages" data-cv-audit-pages>{{ 'cvAudit.pages' | translate:{ count: audit.pages } }}</p>
        @if (!audit.findings.length) {
          <p class="clean" role="status" data-cv-audit-clean><mat-icon aria-hidden="true">verified</mat-icon>{{ 'cvAudit.clean' | translate }}</p>
        }
        <ul class="findings">
          @for (finding of audit.findings; track $index) {
            <li [class]="finding.severity.toLowerCase()" [attr.data-cv-audit-finding]="finding.check">
              <mat-icon aria-hidden="true">{{ icon(finding) }}</mat-icon>
              <span>
                <span class="cdk-visually-hidden">{{ 'cvAudit.severity.' + finding.severity | translate }}:</span>
                {{ 'cvAudit.checks.' + finding.check | translate:{ block: finding.block ?? '', detail: finding.detail ?? '' } }}
              </span>
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .busy { display: flex; justify-content: center; padding: 24px; }
    .pages { margin: 12px 0 4px; color: var(--mat-sys-on-surface-variant); }
    .clean { display: flex; align-items: center; gap: 6px; color: var(--mat-sys-primary); }
    .findings { list-style: none; margin: 8px 0 0; padding: 0; }
    .findings li { display: flex; gap: 8px; align-items: flex-start; padding: 6px 0; border-top: 1px solid var(--mat-sys-outline-variant); }
    .findings mat-icon { flex: none; width: 20px; height: 20px; font-size: 20px; }
    .error mat-icon { color: var(--mat-sys-error); }
    .warning mat-icon { color: var(--mat-sys-tertiary); }
    .info mat-icon { color: var(--mat-sys-on-surface-variant); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CvAuditDialogComponent {
  private readonly service = inject(CvProfileService);
  private readonly changes = inject(ChangeDetectorRef);
  readonly profile = inject<{ profile: CvProfile }>(MAT_DIALOG_DATA).profile;

  layout: 'NAMED' | 'ANONYMOUS' = 'ANONYMOUS';
  audit: CvAudit | null = null;

  constructor() {
    this.load();
  }

  load(): void {
    this.audit = null;
    this.service.audit(this.profile.id, this.layout).subscribe({
      next: audit => {
        this.audit = audit;
        this.changes.markForCheck();
      },
      error: () => {
        this.audit = { pages: 0, findings: [] };
        this.changes.markForCheck();
      }
    });
  }

  icon(finding: CvAuditFinding): string {
    return finding.severity === 'ERROR' ? 'error' : finding.severity === 'WARNING' ? 'warning' : 'info';
  }
}
