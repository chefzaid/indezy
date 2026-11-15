import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { POST_KINDS, PostDetails, PostKind } from '../../../models';

/** The highest daily rate a post names, in euros. */
export const RATE_MAX = 5000;

/**
 * The composer's choice of what a post is, and the facts a mission to pass on or an availability carries: the role (a
 * mission names it), the rate range, the city, remote days, the start (an availability names it), the client if named
 * and the skills.
 */
@Component({
  selector: 'app-post-kind-fields',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './post-kind-fields.component.html',
  styles: [`.kind { width: 260px; max-width: 100%; } .facts { display: flex; flex-wrap: wrap; gap: 0 12px; }
    .facts mat-form-field { flex: 1 1 160px; }`],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PostKindFieldsComponent {
  readonly kinds = POST_KINDS;
  readonly rateMax = RATE_MAX;
  readonly kind = new FormControl<PostKind>('POST', { nonNullable: true });
  readonly details = new FormGroup({
    role: new FormControl('', { nonNullable: true, validators: Validators.maxLength(100) }),
    rateMin: new FormControl<number | null>(null, [Validators.min(0), Validators.max(RATE_MAX)]),
    rateMax: new FormControl<number | null>(null, [Validators.min(0), Validators.max(RATE_MAX)]),
    city: new FormControl('', { nonNullable: true, validators: Validators.maxLength(100) }),
    remoteDays: new FormControl<number | null>(null, [Validators.min(0), Validators.max(5)]),
    start: new FormControl('', { nonNullable: true }),
    client: new FormControl('', { nonNullable: true, validators: Validators.maxLength(100) }),
    stack: new FormControl('', { nonNullable: true })
  });

  /** Whether the kind carries facts. */
  get withFacts(): boolean {
    return this.kind.value === 'MISSION' || this.kind.value === 'AVAILABLE';
  }

  /** Whether the facts are complete for the kind: a mission's role, an availability's start, the rates in order. */
  get valid(): boolean {
    if (!this.withFacts) {
      return true;
    }
    const { role, rateMin, rateMax, start } = this.details.getRawValue();
    return this.details.valid && (this.kind.value !== 'MISSION' || role.trim() !== '') &&
      (this.kind.value !== 'AVAILABLE' || start !== '') && (rateMin === null || rateMax === null || rateMin <= rateMax);
  }

  /** The facts to post with the text; none for a kind without. */
  facts(): PostDetails | null {
    if (!this.withFacts) {
      return null;
    }
    const value = this.details.getRawValue();
    const text = (field: string): string | null => field.trim() || null;
    return { role: text(value.role), rateMin: value.rateMin, rateMax: value.rateMax, city: text(value.city), remoteDays: value.remoteDays,
      start: value.start || null, client: this.kind.value === 'MISSION' ? text(value.client) : null,
      stack: value.stack.split(',').map(skill => skill.trim()).filter(skill => skill !== '').slice(0, 10) };
  }

  reset(): void {
    this.kind.setValue('POST');
    this.details.reset();
  }
}
