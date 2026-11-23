import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DocumentTemplate, DocumentTemplateKind } from '../../../models';
import { BillingService, DocumentLayout } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { copyExtension, saveBlob } from '../../../shared/utils/download-file';

/**
 * The Word templates (or fillable PDF forms) of the invoices and the quotes (see docs/features.md#invoices): each uploaded with its placeholders,
 * shown with those it uses, those unknown and the mentions it lacks, which the filled documents add at their end.
 */
@Component({
  selector: 'app-billing-templates',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, RouterLink, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="templates" aria-labelledby="billing-templates-title" data-billing-templates>
      <h2 id="billing-templates-title">{{ 'billingTemplates.title' | translate }}</h2>
      <p class="hint">{{ 'billingTemplates.hint' | translate:{ example: example } }}
        <a routerLink="/help/document-templates" data-billing-templates-guide>{{ 'billingTemplates.guide' | translate }}</a></p>
      @for (kind of kinds; track kind) {
        <div class="kind" [attr.data-billing-template]="kind">
          <h3>{{ 'billingTemplates.kinds.' + kind | translate }}</h3>
          <input #file type="file" hidden accept=".docx,.pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/pdf"
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
            <button mat-button type="button" [matMenuTriggerFor]="starterMenu" [attr.data-billing-template-starter]="kind">
              <mat-icon>download</mat-icon>{{ 'billingTemplates.starter' | translate }}
            </button>
            <mat-menu #starterMenu="matMenu">
              <button mat-menu-item type="button" (click)="starter(kind)" [attr.data-billing-starter-layout]="kind + '-OWN'">
                {{ 'billingTemplates.starterOwn' | translate }}
              </button>
              @for (layout of layouts; track layout) {
                <button mat-menu-item type="button" (click)="starter(kind, layout)" [attr.data-billing-starter-layout]="kind + '-' + layout">
                  {{ 'billingTemplates.starterLayout' | translate:{ layout: ('profile.billing.themes.' + layout | translate) } }}
                </button>
              }
              <button mat-menu-item type="button" (click)="starterExample(kind)" [attr.data-billing-starter-example]="kind">
                <mat-icon>description</mat-icon>{{ 'billingTemplates.starterExample' | translate }}
              </button>
            </mat-menu>
            @if (template(kind)) {
              <button mat-button type="button" (click)="preview(kind)" [attr.data-billing-template-preview]="kind">
                <mat-icon>visibility</mat-icon>{{ 'billingTemplates.preview' | translate }}
              </button>
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
  private readonly translate = inject(TranslateService);

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

  /** The translation key of a placeholder's meaning: "#lines" as "lines", "/lines" as "linesEnd", "^vatExempt" as "not_vatExempt". */
  key(placeholder: string): string {
    const name = placeholder.slice(1);
    return placeholder.startsWith('#') ? name : placeholder.startsWith('/') ? `${name}End` : placeholder.startsWith('^') ? `not_${name}` :
      placeholder.replace('.', '_');
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
    if (file.size > 5 * 1024 * 1024 || !/\.(docx|pdf)$/i.test(file.name)) {
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
        } else if (/no form field/.test(reason)) {
          this.notifications.error('billingTemplates.noField');
        } else if (/protected|scripts/.test(reason)) {
          this.notifications.error('billingTemplates.pdfRefused');
        } else {
          this.notifications.error(/macro/.test(reason) ? 'billingTemplates.macros' : 'billingTemplates.refused');
        }
        this.changes.markForCheck();
      }
    });
  }

  readonly layouts: DocumentLayout[] = ['SIMPLE', 'MODERN', 'CLASSIC'];

  /** Downloads a Word template to start from, in the app's language: a built-in layout with its placeholders in place. */
  starter(kind: DocumentTemplateKind, layout?: DocumentLayout): void {
    const english = this.translate.getCurrentLang() === 'en';
    this.billing.starterTemplate(kind, english ? 'EN' : 'FR', layout).subscribe({
      next: blob => saveBlob(blob, kind === 'INVOICE' ? (english ? 'invoice-template.docx' : 'modele-facture.docx') :
        (english ? 'quote-template.docx' : 'modele-devis.docx')),
      error: () => this.notifications.error('billingTemplates.previewError')
    });
  }

  /** Downloads the template to start from filled with a sample document, to see what it becomes once uploaded. */
  starterExample(kind: DocumentTemplateKind): void {
    const english = this.translate.getCurrentLang() === 'en';
    this.billing.starterExample(kind, english ? 'EN' : 'FR').subscribe({
      next: blob => saveBlob(blob, kind === 'INVOICE' ? (english ? 'invoice-example.docx' : 'exemple-facture.docx') :
        (english ? 'quote-example.docx' : 'exemple-devis.docx')),
      error: () => this.notifications.error('billingTemplates.previewError')
    });
  }

  /** Downloads the template filled with the latest document, or a sample before the first. */
  preview(kind: DocumentTemplateKind): void {
    this.billing.templatePreview(kind).subscribe({
      next: blob => saveBlob(blob, (kind === 'INVOICE' ? 'apercu-modele-facture.' : 'apercu-modele-devis.') + copyExtension(blob)),
      error: () => this.notifications.error('billingTemplates.previewError')
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
