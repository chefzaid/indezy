import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
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

const COMPANIES: LegalForm[] = ['EURL', 'SASU', 'SARL', 'SAS'];

/** The seller's identity on the workspace's invoices: legal name and form, SIRET, VAT, address and bank details. */
@Component({
  selector: 'app-profile-billing',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, TranslateModule],
  templateUrl: './profile-billing.component.html',
  styleUrls: ['./profile-billing.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileBillingComponent implements OnInit {
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
    invoicePrefix: ['F', [Validators.pattern(/^[A-Za-z0-9-]{0,10}$/)]]
  });
  complete = false;
  saving = false;

  get company(): boolean {
    return COMPANIES.includes(this.form.controls.legalForm.value as LegalForm);
  }

  get portage(): boolean {
    return this.form.controls.legalForm.value === 'PORTAGE';
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

  save(): void {
    if (this.form.invalid || this.saving) {
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
      invoicePrefix: value.invoicePrefix?.trim() || null
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
    this.form.reset({
      legalName: identity.legalName ?? '', legalForm: identity.legalForm, siret: identity.siret ?? '', vatExempt: identity.vatExempt,
      vatNumber: identity.vatNumber ?? '', vatRate: identity.vatRate, shareCapital: identity.shareCapital,
      registration: identity.registration ?? '', address: identity.address ?? '', iban: identity.iban ?? '', bic: identity.bic ?? '',
      paymentTermDays: identity.paymentTermDays, invoicePrefix: identity.invoicePrefix ?? ''
    });
  }
}
