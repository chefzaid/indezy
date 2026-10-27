import { ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { SLUG_PATTERN, slugFrom } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { PublicProfileService } from '../../../services/public-profile/public-profile.service';

/**
 * The public profile's settings: whether anyone with the link sees it, and its address name, offered from the
 * freelancer's name the first time; the link, once saved, is copied or opened from here.
 */
@Component({
  selector: 'app-profile-public-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSlideToggleModule,
    TranslateModule],
  templateUrl: './profile-public-page.component.html',
  styleUrls: ['./profile-public-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfilePublicPageComponent implements OnInit, OnDestroy {
  private readonly profiles = inject(PublicProfileService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly destroy$ = new Subject<void>();

  /** The freelancer's name, to offer an address from when none is chosen yet. */
  @Input() name = '';

  readonly form = inject(FormBuilder).nonNullable.group({
    enabled: false,
    slug: ['', [Validators.minLength(3), Validators.maxLength(40), Validators.pattern(SLUG_PATTERN)]]
  });
  /** The address as saved, which the link shows. */
  savedSlug: string | null = null;
  savedEnabled = false;
  isSaving = false;

  ngOnInit(): void {
    this.profiles.settings().pipe(takeUntil(this.destroy$)).subscribe({
      next: settings => {
        this.savedSlug = settings.slug;
        this.savedEnabled = settings.enabled;
        this.form.setValue({ enabled: settings.enabled, slug: settings.slug ?? slugFrom(this.name) });
      },
      error: () => this.notifications.error('profile.publicPage.loadError')
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get link(): string | null {
    return this.savedSlug ? `${globalThis.location.origin}/p/${this.savedSlug}` : null;
  }

  save(): void {
    const { enabled, slug } = this.form.getRawValue();
    const chosen = slug.trim().toLowerCase();
    if (enabled && !chosen) {
      this.form.controls.slug.setErrors({ required: true });
    }
    if (this.form.invalid || this.isSaving) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSaving = true;
    this.profiles.save({ enabled, slug: chosen || null }).pipe(takeUntil(this.destroy$)).subscribe({
      next: saved => {
        this.isSaving = false;
        this.savedSlug = saved.slug;
        this.savedEnabled = saved.enabled;
        this.form.setValue({ enabled: saved.enabled, slug: saved.slug ?? '' });
        this.notifications.success(saved.enabled ? 'profile.publicPage.shown' : 'profile.publicPage.hidden');
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        if (error.status === 409) {
          this.form.controls.slug.setErrors({ taken: true });
          return;
        }
        this.notifications.error('profile.publicPage.saveError');
      }
    });
  }

  copyLink(): void {
    if (this.link && this.clipboard.copy(this.link)) {
      this.notifications.success('profile.publicPage.copied');
    }
  }
}
