import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { DocumentTemplate, DocumentTemplateKind } from '../../../models';
import { BillingService } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * The Word templates of the invoices and the quotes (see docs/features.md#invoices): each uploaded with its placeholders,
 * shown with those it uses, those unknown and the mentions it lacks, which the filled documents add at their end.
 */
@Component({
  selector: 'app-billing-templates',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="templates" aria-labelledby="billing-templates-title" data-billing-templates>
      <h2 id="billing-templates-title">{{ 'billingTemplates.title' | translate }}</h2>
      <p class="hint">{{ 'billingTemplates.hint' | translate:{ example: example } }}</p>
      @for (kind of kinds; track kind) {
        <div class="kind" [attr.data-billing-template]="kind">
          <h3>{{ 'billingTemplates.kinds.' + kind | translate }}</h3>
          <input #file type="file" hidden accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            (change)="upload(kind, $event)" [attr.data-billing-template-file]="kind" [attr.aria-label]="'billingTemplates.upload' | translate">
          @if (template(kind); as current) {
            <p class="file"><mat-icon aria-hidden="true">description</mat-icon>{{ current.fileName }}</p>
            <p class="found">{{ 'billingTemplates.found' | translate:{ count: current.found.length } }}</p>
            @if (current.unknown.length) {
              <p class="warning" data-billing-template-unknown>{{ 'billingTemplates.unknown' | translate:{ names: braces(current.unknown) } }}</p>
            }
            @if (current.missing.length) {
              <p class="warning" data-billing-template-missing>{{ 'billingTemplates.missing' | translate:{ mentions: missingNames(current.missing) } }}</p>
            }
          } @else {
            <p class="hint">{{ 'billingTemplates.none' | translate }}</p>
          }
          <div class="actions">
            <button mat-stroked-button type="button" (click)="file.click()" [disabled]="busy" [attr.data-billing-template-upload]="kind">
              <mat-icon>upload_file</mat-icon>{{ (template(kind) ? 'billingTemplates.replace' : 'billingTemplates.upload') | translate }}
            </button>
            @if (template(kind)) {
              <button mat-button type="button" (click)="remove(kind)" [attr.data-billing-template-remove]="kind">
                {{ 'billingTemplates.remove' | translate }}
              </button>
            }
          </div>
        </div>
      }
      <details class="placeholders" data-billing-placeholders>
        <summary>{{ 'billingTemplates.placeholders' | translate }}</summary>
        <p class="hint">{{ 'billingTemplates.placeholdersHint' | translate:aliases }}</p>
        <ul>
          @for (placeholder of placeholders; track placeholder) {
            <li>
              <button mat-button type="button" (click)="copy(placeholder)" [attr.aria-label]="'billingTemplates.copy' | translate:{ name: placeholder }">
                <code>{{ braces([placeholder]) }}</code>
              </button>
              <span>{{ 'billingTemplates.meaning.' + key(placeholder) | translate }}</span>
            </li>
          }
        </ul>
      </details>
    </section>
  `,
  styles: [`
    .templates { margin-top: 24px; }
    .templates h2 { margin: 0 0 8px; font-size: 1.15rem; }
    .kind { margin: 12px 0; }
    .kind h3 { margin: 0 0 4px; font-size: 1rem; }
    .hint, .found { color: var(--app-text-muted); font-size: 0.9rem; margin: 0 0 6px; }
    .warning { color: var(--app-warning); font-size: 0.9rem; margin: 0 0 6px; overflow-wrap: anywhere; }
    .file { display: flex; align-items: center; gap: 6px; margin: 0 0 4px; overflow-wrap: anywhere; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .placeholders ul { list-style: none; padding: 0; margin: 0; display: grid; gap: 2px; }
    .placeholders li { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; }
    .placeholders li span { color: var(--app-text-muted); font-size: 0.85rem; }
  `]
})
export class BillingTemplatesComponent implements OnInit {
  private readonly billing = inject(BillingService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly kinds: DocumentTemplateKind[] = ['INVOICE', 'QUOTE'];
  /** Placeholders written in the texts, kept out of the translations' own braces. */
  readonly example = '{{invoice.number}}';
  readonly aliases = { invoice: '{{invoice.…}}', quote: '{{quote.…}}', document: '{{document.…}}' };
  templates: DocumentTemplate[] = [];
  placeholders: string[] = [];
  busy = false;

  ngOnInit(): void {
    this.billing.templates().subscribe({ next: templates => this.show(templates), error: () => this.show([]) });
    this.billing.placeholders().subscribe({
      next: placeholders => {
        this.placeholders = placeholders;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  template(kind: DocumentTemplateKind): DocumentTemplate | undefined {
    return this.templates.find(template => template.kind === kind);
  }

  /** The translation key of a placeholder's meaning: "#lines" as "lines", "/lines" as "linesEnd". */
  key(placeholder: string): string {
    return placeholder === '#lines' ? 'lines' : placeholder === '/lines' ? 'linesEnd' : placeholder.replace('.', '_');
  }

  braces(names: string[]): string {
    return names.map(name => `{{${name}}}`).join(', ');
  }

  missingNames(missing: string[]): string {
    return missing.join(', ');
  }

  upload(kind: DocumentTemplateKind, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > 5 * 1024 * 1024 || !/\.docx$/i.test(file.name)) {
      this.notifications.error('billingTemplates.refused');
      return;
    }
    this.busy = true;
    this.billing.uploadTemplate(kind, file).subscribe({
      next: template => {
        this.busy = false;
        this.show([...this.templates.filter(each => each.kind !== kind), template]);
        this.notifications.success('billingTemplates.saved');
      },
      error: (error: HttpErrorResponse) => {
        this.busy = false;
        const reason = error.status === 400 ? (error.error as { message?: string } | null)?.message ?? '' : '';
        if (/placeholder/.test(reason)) {
          this.notifications.error('billingTemplates.noPlaceholder', undefined, { example: this.example });
        } else {
          this.notifications.error(/macro/.test(reason) ? 'billingTemplates.macros' : 'billingTemplates.refused');
        }
        this.changes.markForCheck();
      }
    });
  }

  remove(kind: DocumentTemplateKind): void {
    this.billing.removeTemplate(kind).subscribe({
      next: () => this.show(this.templates.filter(template => template.kind !== kind)),
      error: () => this.notifications.error('profile.billing.saveError')
    });
  }

  copy(placeholder: string): void {
    void navigator.clipboard?.writeText(`{{${placeholder}}}`).then(() => this.notifications.success('billingTemplates.copied'), () => undefined);
  }

  private show(templates: DocumentTemplate[]): void {
    this.templates = templates;
    this.changes.markForCheck();
  }
}
