import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { BillingIdentity, LEGAL_FORMS, LegalForm } from '../../../models';
import { BillingService } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { billingId, compactId } from '../../../shared/utils/billing-validators';
import { fieldError } from '../../../shared/utils/form-errors';
import { HelpLinkComponent } from '../../help/help-link.component';

const COMPANIES: LegalForm[] = ['EURL', 'SASU', 'SARL', 'SAS'];

/** The seller's identity on the workspace's invoices: legal name and form, SIRET, VAT, address and bank details. */
@Component({
  selector: 'app-profile-billing',
  imports: [HelpLinkComponent, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
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
    accentColor: ['']
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

  ngOnDestroy(): void {
    this.revokeLogo();
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
        this.notifications.success('profile.billing.logoSaved');
      },
      error: () => {
        this.uploadingLogo = false;
        this.notifications.error('profile.billing.logoRefused');
      }
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

  setAccent(event: Event): void {
    this.form.patchValue({ accentColor: (event.target as HTMLInputElement).value.toUpperCase() });
    this.form.markAsDirty();
  }

  clearAccent(): void {
    this.form.patchValue({ accentColor: '' });
    this.form.markAsDirty();
  }

  save(): void {
    if (this.form.invalid || this.saving || this.accentTooPale) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const identity: BillingIdentity = {
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
      accentColor: value.accentColor || null
    };
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

  private show(identity: BillingIdentity): void {
    this.complete = !!identity.complete;
    this.showLogo(identity.logoId ?? null);
    this.form.reset({
      legalName: identity.legalName ?? '', legalForm: identity.legalForm, siret: identity.siret ?? '', vatExempt: identity.vatExempt,
      vatNumber: identity.vatNumber ?? '', vatRate: identity.vatRate, shareCapital: identity.shareCapital,
      registration: identity.registration ?? '', address: identity.address ?? '', iban: identity.iban ?? '', bic: identity.bic ?? '',
      paymentTermDays: identity.paymentTermDays, invoicePrefix: identity.invoicePrefix ?? '',
      latePaymentRate: identity.latePaymentRate ?? null,
      quoteTerms: identity.quoteTerms ?? '',
      accentColor: identity.accentColor ?? ''
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
