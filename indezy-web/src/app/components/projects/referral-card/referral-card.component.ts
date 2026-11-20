import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, switchMap } from 'rxjs';

import { ProjectDto } from '../../../models';
import { ProjectReferral, ProjectReferralView, ReferralBonusKind, ReferralDirection, ReferralStatus } from '../../../models/project.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectReferralService } from '../../../services/project/project-referral.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

/** The member who referred an opportunity added from the network, as its source names them (`Indezy @handle`). */
export function referrerFromSource(sourceName: string | null | undefined): string {
  const handle = /^Indezy (@[a-z0-9_]+)$/i.exec(sourceName ?? '');
  return handle ? handle[1] : '';
}

/**
 * An opportunity's co-optation (see docs/features.md#co-optation): who referred it to the freelancer, or whom they
 * referred, and the bonus that goes with it, fixed, per day worked or a share of what the mission earns, counted from its
 * timesheets, promised, due or paid.
 */
@Component({
  selector: 'app-referral-card',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './referral-card.component.html',
  styleUrls: ['./referral-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ReferralCardComponent implements OnChanges {
  private readonly referrals = inject(ProjectReferralService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) project!: ProjectDto;

  readonly directions: ReferralDirection[] = ['I_PAY', 'I_RECEIVE'];
  readonly kinds: ReferralBonusKind[] = ['FIXED', 'PER_DAY', 'PERCENT'];
  readonly statuses: ReferralStatus[] = ['PROMISED', 'DUE', 'PAID'];
  readonly form = new FormGroup({
    referrer: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    direction: new FormControl<ReferralDirection>('I_PAY', { nonNullable: true }),
    bonusKind: new FormControl<ReferralBonusKind | null>(null),
    bonusAmount: new FormControl<number | null>(null, { validators: [Validators.min(0), Validators.max(1000000)] }),
    status: new FormControl<ReferralStatus>('PROMISED', { nonNullable: true }),
    paidOn: new FormControl('', { nonNullable: true }),
    note: new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) })
  });

  view: ProjectReferralView | null = null;
  loading = true;
  editing = false;
  saving = false;

  ngOnChanges(): void {
    this.loading = true;
    this.referrals.get(this.project.id!).subscribe({
      next: view => {
        // None saved answers 204, with no body.
        this.view = view?.referral ? view : null;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  /** Whether the amount is missing for the bonus chosen, or set without one, or a share above 100%. */
  get amountInvalid(): boolean {
    const { bonusKind, bonusAmount } = this.form.getRawValue();
    return (bonusKind === null) !== (bonusAmount === null) || (bonusKind === 'PERCENT' && (bonusAmount ?? 0) > 100);
  }

  edit(): void {
    const saved = this.view?.referral;
    this.form.reset({
      referrer: saved?.referrer ?? referrerFromSource(this.project.sourceName), direction: saved?.direction ?? 'I_PAY',
      bonusKind: saved?.bonusKind ?? null, bonusAmount: saved?.bonusAmount ?? null, status: saved?.status ?? 'PROMISED',
      paidOn: saved?.paidOn ?? '', note: saved?.note ?? ''
    });
    this.editing = true;
  }

  /** No bonus: no amount either. */
  kindChanged(): void {
    if (this.form.controls.bonusKind.value === null) {
      this.form.controls.bonusAmount.setValue(null);
    }
  }

  save(): void {
    if (this.form.invalid || this.amountInvalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const referral: ProjectReferral = { referrer: value.referrer.trim(), direction: value.direction, bonusKind: value.bonusKind,
      bonusAmount: value.bonusAmount, status: value.status, paidOn: value.status === 'PAID' && value.paidOn ? value.paidOn : null,
      note: value.note.trim() || null };
    this.saving = true;
    this.referrals.save(this.project.id!, referral).subscribe({
      next: view => {
        this.saving = false;
        this.editing = false;
        this.view = view;
        this.notifications.success('referral.saved');
        this.changes.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.notifications.error('referral.saveError');
        this.changes.markForCheck();
      }
    });
  }

  /** Removes the co-optation once confirmed. */
  remove(): void {
    this.confirm.confirm({ messageKey: 'referral.removeConfirm', confirmKey: 'common.delete', danger: true })
      .pipe(switchMap(confirmed => confirmed ? this.referrals.remove(this.project.id!) : EMPTY))
      .subscribe({
        next: () => {
          this.view = null;
          this.changes.markForCheck();
        },
        error: () => this.notifications.error('referral.saveError')
      });
  }
}
