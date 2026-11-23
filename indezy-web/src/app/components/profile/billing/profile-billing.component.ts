import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { BillingIdentity, LEGAL_FORMS, LegalForm } from '../../../models';
import { BillingService } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { billingId, compactId } from '../../../shared/utils/billing-validators';
import { fieldError } from '../../../shared/utils/form-errors';
import { HelpLinkComponent } from '../../help/help-link.component';
import { saveBlob } from '../../../shared/utils/download-file';

const COMPANIES: LegalForm[] = ['EURL', 'SASU', 'SARL', 'SAS'];

/** The seller's identity on the workspace's invoices: legal name and form, SIRET, VAT, address and bank details. */
@Component({
  selector: 'app-profile-billing',
  imports: [HelpLinkComponent, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatCheckboxModule, MatSelectModule,
    MatSlideToggleModule, TranslateModule],
  templateUrl: './profile-billing.component.html',
  styleUrls: ['./profile-billing.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileBillingComponent implements OnInit, OnDestroy {
  private readonly billing = inject(BillingService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  readonly legalForms = LEGAL_FORMS;
  readonly form = inject(FormBuilder).group({
    legalName: ['', [Validators.maxLength(120)]],
    legalForm: [null as LegalForm | null],
    siret: ['', [billingId('siret')]],
    vatExempt: [true],
    vatNumber: ['', [billingId('vatNumber')]],
    vatRate: [20 as number | null, [Validators.min(0), Validators.max(30)]],
    shareCapital: [null as number | null, [Validators.min(0)]],
    registration: ['', [Validators.maxLength(120)]],
    address: ['', [Validators.maxLength(300)]],
    iban: ['', [billingId('iban')]],
    bic: ['', [billingId('bic')]],
    paymentTermDays: [30 as number | null, [Validators.min(0), Validators.max(60)]],
    invoicePrefix: ['F', [Validators.pattern(/^[A-Za-z0-9-]{0,10}$/)]],
    latePaymentRate: [null as number | null, [Validators.min(0), Validators.max(100)]],
    quoteTerms: ['', Validators.maxLength(20000)],
    accentColor: [''],
    header: ['', Validators.maxLength(150)],
    font: ['SANS' as 'SANS' | 'SERIF' | 'CUSTOM'],
    theme: ['SIMPLE' as 'SIMPLE' | 'MODERN' | 'CLASSIC'],
    quoteTheme: [null as 'SIMPLE' | 'MODERN' | 'CLASSIC' | null],
    quoteAccentColor: [''],
    letterheadTop: [null as number | null, [Validators.min(0), Validators.max(120)]],
    letterheadBottom: [null as number | null, [Validators.min(0), Validators.max(120)]],
    footer: ['', Validators.maxLength(200)],
    logoPlacement: ['LEFT' as 'LEFT' | 'CENTER' | 'RIGHT'],
    logoSize: ['MEDIUM' as 'SMALL' | 'MEDIUM' | 'LARGE'],
    invoiceNotes: ['', Validators.maxLength(2000)],
    quoteNotes: ['', Validators.maxLength(2000)]
  });
  complete = false;
  saving = false;

  get company(): boolean {
    return COMPANIES.includes(this.form.controls.legalForm.value as LegalForm);
  }

  get portage(): boolean {
    return this.form.controls.legalForm.value === 'PORTAGE';
  }

  /** The logo shown, as an object URL; null for none. */
  logoUrl: string | null = null;
  logoId: number | null = null;
  uploadingLogo = false;
  termsFileId: number | null = null;
  /** The uploaded font's family; null for none. */
  fontFamily: string | null = null;
  uploadingFont = false;
  letterheadId: number | null = null;
  /** The style's preview, as an object URL; null until asked. */
  previewUrl: string | null = null;
  previewing = false;
  readonly themes = ['SIMPLE', 'MODERN', 'CLASSIC'] as const;

  ngOnDestroy(): void {
    this.revokeLogo();
    this.revokePreview();
  }

  uploadLogo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > 2 * 1024 * 1024 || !/^image\/(png|jpeg)$/.test(file.type)) {
      this.notifications.error('profile.billing.logoRefused');
      return;
    }
    this.uploadingLogo = true;
    this.billing.uploadLogo(file).subscribe({
      next: identity => {
        this.uploadingLogo = false;
        this.showLogo(identity.logoId ?? null);
        this.termsFileId = identity.quoteTermsFileId ?? null;
        this.notifications.success('profile.billing.logoSaved');
      },
      error: () => {
        this.uploadingLogo = false;
        this.notifications.error('profile.billing.logoRefused');
      }
    });
  }

  uploadTermsFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > 5 * 1024 * 1024 || file.type !== 'application/pdf') {
      this.notifications.error('profile.billing.termsFileRefused');
      return;
    }
    this.billing.uploadTermsFile(file).subscribe({
      next: identity => {
        this.termsFileId = identity.quoteTermsFileId ?? null;
        this.notifications.success('profile.billing.termsFileSaved');
      },
      error: () => this.notifications.error('profile.billing.termsFileRefused')
    });
  }

  uploadLetterhead(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > 5 * 1024 * 1024 || file.type !== 'application/pdf') {
      this.notifications.error('profile.billing.letterheadRefused');
      return;
    }
    this.billing.uploadLetterhead(file).subscribe({
      next: identity => {
        this.letterheadId = identity.letterheadId ?? null;
        this.notifications.success('profile.billing.letterheadSaved');
      },
      error: (error: HttpErrorResponse) => {
        const reason = error.status === 400 ? (error.error as { message?: string } | null)?.message ?? '' : '';
        this.notifications.error(/A4/.test(reason) ? 'profile.billing.letterheadSize' : /embed/.test(reason) ? 'profile.billing.letterheadFonts' :
          /one or two/.test(reason) ? 'profile.billing.letterheadPages' : 'profile.billing.letterheadRefused');
      }
    });
  }

  openLetterhead(): void {
    if (this.letterheadId === null) {
      return;
    }
    this.billing.letterhead(this.letterheadId).subscribe({
      next: blob => saveBlob(blob, 'papier-en-tete.pdf'),
      error: () => this.notifications.error('profile.billing.loadError')
    });
  }

  removeLetterhead(): void {
    this.billing.removeLetterhead().subscribe({
      next: () => this.letterheadId = null,
      error: () => this.notifications.error('profile.billing.saveError')
    });
  }

  removeTermsFile(): void {
    this.billing.removeTermsFile().subscribe({
      next: () => this.termsFileId = null,
      error: () => this.notifications.error('profile.billing.saveError')
    });
  }

  openTermsFile(): void {
    if (this.termsFileId === null) {
      return;
    }
    this.billing.termsFile(this.termsFileId).subscribe({
      next: blob => saveBlob(blob, 'cgv.pdf'),
      error: () => this.notifications.error('profile.billing.loadError')
    });
  }

  /** Uploads the font chosen: one TrueType file, or two when the bold face comes with it (the one named bold). */
  uploadFont(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = [...(input.files ?? [])];
    input.value = '';
    if (!files.length) {
      return;
    }
    if (files.length > 2 || files.some(file => file.size > 5 * 1024 * 1024 || !/\.(ttf|otf)$/i.test(file.name))) {
      this.notifications.error('profile.billing.fontRefused');
      return;
    }
    const bold = files.length === 2 ? files.find(file => /bold|gras/i.test(file.name)) ?? files[1] : null;
    const regular = files.find(file => file !== bold) ?? files[0];
    this.uploadingFont = true;
    this.billing.uploadFont(regular, bold).subscribe({
      next: identity => {
        this.uploadingFont = false;
        this.fontFamily = identity.fontFamily ?? null;
    this.letterheadId = identity.letterheadId ?? null;
        this.form.patchValue({ font: identity.font ?? 'CUSTOM' });
        this.notifications.success('profile.billing.fontSaved', undefined, { family: this.fontFamily });
      },
      error: (error: HttpErrorResponse) => {
        this.uploadingFont = false;
        const reason = error.status === 400 ? (error.error as { message?: string } | null)?.message ?? '' : '';
        this.notifications.error(/licen[cs]e/i.test(reason) ? 'profile.billing.fontLicence' : /CFF/.test(reason) ? 'profile.billing.fontCff' :
          /lacks letters/.test(reason) ? 'profile.billing.fontLetters' : 'profile.billing.fontRefused');
      }
    });
  }

  removeFont(): void {
    this.billing.removeFont().subscribe({
      next: identity => {
        this.fontFamily = null;
        this.form.patchValue({ font: identity.font ?? 'SANS' });
      },
      error: () => this.notifications.error('profile.billing.saveError')
    });
  }

  removeLogo(): void {
    this.billing.removeLogo().subscribe({
      next: () => this.showLogo(null),
      error: () => this.notifications.error('profile.billing.saveError')
    });
  }

  private showLogo(id: number | null): void {
    if (id === this.logoId) {
      return;
    }
    this.logoId = id;
    this.revokeLogo();
    if (id !== null) {
      this.billing.logo(id).subscribe({
        next: blob => this.logoUrl = URL.createObjectURL(blob),
        error: () => undefined
      });
    }
  }

  private revokeLogo(): void {
    if (this.logoUrl) {
      URL.revokeObjectURL(this.logoUrl);
      this.logoUrl = null;
    }
  }

  ngOnInit(): void {
    this.billing.identity().subscribe({
      next: identity => this.show(identity),
      error: () => this.notifications.error('profile.billing.loadError')
    });
  }

  error(field: string): string {
    return fieldError(this.form.get(field), this.translate);
  }

  /** Whether the chosen accent colour is too pale to read on white paper or print (a contrast under 3 to 1). */
  get accentTooPale(): boolean {
    const color = this.form.value.accentColor;
    return !!color && contrastOnWhite(color) < 3;
  }

  /** Whether the quotes have their own colour and layout, the rest shared with the invoices. */
  ownQuoteStyle = false;

  get quoteAccentTooPale(): boolean {
    const color = this.ownQuoteStyle ? this.form.value.quoteAccentColor : null;
    return !!color && contrastOnWhite(color) < 3;
  }

  /** Gives the quotes their own style, starting from the invoices', or takes it back. */
  setOwnQuoteStyle(own: boolean): void {
    this.ownQuoteStyle = own;
    this.form.patchValue(own ? { quoteTheme: this.form.value.theme ?? 'SIMPLE', quoteAccentColor: this.form.value.accentColor ?? '' }
      : { quoteTheme: null, quoteAccentColor: '' });
    this.form.markAsDirty();
  }

  setQuoteAccent(event: Event): void {
    this.form.patchValue({ quoteAccentColor: (event.target as HTMLInputElement).value.toUpperCase() });
    this.form.markAsDirty();
  }

  setAccent(event: Event): void {
    this.form.patchValue({ accentColor: (event.target as HTMLInputElement).value.toUpperCase() });
    this.form.markAsDirty();
  }

  clearAccent(): void {
    this.form.patchValue({ accentColor: '' });
    this.form.markAsDirty();
  }

  save(): void {
    if (this.form.invalid || this.saving || this.accentTooPale || this.quoteAccentTooPale) {
      this.form.markAllAsTouched();
      return;
    }
    const identity = this.formIdentity();
    this.saving = true;
    this.billing.saveIdentity(identity).subscribe({
      next: saved => {
        this.show(saved);
        this.saving = false;
        this.notifications.success('profile.billing.saved');
      },
      error: () => {
        this.saving = false;
        this.notifications.error('profile.billing.saveError');
      }
    });
  }

  /** Draws the first page of an invoice in the style the form sets, before it is saved. */
  /** Draws the first page of an invoice, or of a quote in the quotes' own style, as the form holds it. */
  preview(quote = false): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.previewing = true;
    this.billing.stylePreview(this.formIdentity(), quote).subscribe({
      next: image => {
        this.previewing = false;
        this.revokePreview();
        this.previewUrl = URL.createObjectURL(image);
      },
      error: () => {
        this.previewing = false;
        this.notifications.error('profile.billing.previewError');
      }
    });
  }

  private revokePreview(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = null;
    }
  }

  /** The identity the form holds, as saved. */
  private formIdentity(): BillingIdentity {
    const value = this.form.getRawValue();
    return {
      legalName: value.legalName?.trim() || null,
      legalForm: value.legalForm,
      siret: compactId(value.siret) || null,
      vatExempt: !!value.vatExempt,
      vatNumber: compactId(value.vatNumber, true) || null,
      vatRate: value.vatRate,
      shareCapital: this.company ? value.shareCapital : null,
      registration: this.company ? value.registration?.trim() || null : null,
      address: value.address?.trim() || null,
      iban: compactId(value.iban, true) || null,
      bic: compactId(value.bic, true) || null,
      paymentTermDays: value.paymentTermDays,
      invoicePrefix: value.invoicePrefix?.trim() || null,
      latePaymentRate: value.latePaymentRate ?? null,
      quoteTerms: value.quoteTerms?.trim() || null,
      accentColor: value.accentColor || null,
      header: value.header?.trim() || null,
      font: value.font ?? 'SANS',
      theme: value.theme ?? 'SIMPLE',
      letterheadTop: value.letterheadTop ?? null,
      letterheadBottom: value.letterheadBottom ?? null,
      quoteTheme: this.ownQuoteStyle ? value.quoteTheme ?? null : null,
      quoteAccentColor: this.ownQuoteStyle ? value.quoteAccentColor || null : null,
      footer: value.footer?.trim() || null,
      logoPlacement: value.logoPlacement ?? 'LEFT',
      logoSize: value.logoSize ?? 'MEDIUM',
      invoiceNotes: value.invoiceNotes?.trim() || null,
      quoteNotes: value.quoteNotes?.trim() || null
    };
  }

  private show(identity: BillingIdentity): void {
    this.complete = !!identity.complete;
    this.fontFamily = identity.fontFamily ?? null;
    this.letterheadId = identity.letterheadId ?? null;
    this.ownQuoteStyle = !!(identity.quoteTheme || identity.quoteAccentColor);
    this.showLogo(identity.logoId ?? null);
    this.form.reset({
      legalName: identity.legalName ?? '', legalForm: identity.legalForm, siret: identity.siret ?? '', vatExempt: identity.vatExempt,
      vatNumber: identity.vatNumber ?? '', vatRate: identity.vatRate, shareCapital: identity.shareCapital,
      registration: identity.registration ?? '', address: identity.address ?? '', iban: identity.iban ?? '', bic: identity.bic ?? '',
      paymentTermDays: identity.paymentTermDays, invoicePrefix: identity.invoicePrefix ?? '',
      latePaymentRate: identity.latePaymentRate ?? null,
      quoteTerms: identity.quoteTerms ?? '',
      accentColor: identity.accentColor ?? '',
      header: identity.header ?? '',
      font: identity.font ?? 'SANS',
      theme: identity.theme ?? 'SIMPLE',
      letterheadTop: identity.letterheadTop ?? null,
      letterheadBottom: identity.letterheadBottom ?? null,
      quoteTheme: identity.quoteTheme ?? null,
      quoteAccentColor: identity.quoteAccentColor ?? '',
      footer: identity.footer ?? '',
      logoPlacement: identity.logoPlacement ?? 'LEFT',
      logoSize: identity.logoSize ?? 'MEDIUM',
      invoiceNotes: identity.invoiceNotes ?? '',
      quoteNotes: identity.quoteNotes ?? ''
    });
  }
}

/** The contrast ratio of a #RRGGBB colour on white, from 1 to 21, as WCAG measures it (the server checks the same). */
export function contrastOnWhite(hex: string): number {
  const linear = (at: number): number => {
    const channel = parseInt(hex.substring(at, at + 2), 16) / 255;
    return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
  };
  const luminance = 0.2126 * linear(1) + 0.7152 * linear(3) + 0.0722 * linear(5);
  return 1.05 / (luminance + 0.05);
}
