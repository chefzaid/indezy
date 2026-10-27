import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Title } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { PublicProfile } from '../../models';
import { PublicProfileService } from '../../services/public-profile/public-profile.service';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';

/** A freelancer's public profile, opened from its link without an account. */
@Component({
  selector: 'app-public-profile',
  imports: [CommonModule, MatCardModule, MatChipsModule, MatIconModule, MatProgressSpinnerModule, TranslateModule,
    PublicPageHeaderComponent],
  templateUrl: './public-profile.component.html',
  styleUrls: ['./public-profile.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PublicProfileComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly profiles = inject(PublicProfileService);
  private readonly title = inject(Title);
  private readonly destroy$ = new Subject<void>();

  profile?: PublicProfile;
  isLoading = true;
  notFound = false;

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug') ?? '';
    this.profiles.view(slug).pipe(takeUntil(this.destroy$)).subscribe({
      next: profile => {
        this.profile = profile;
        this.isLoading = false;
        this.title.setTitle(`${this.fullName} – Indezy`);
      },
      error: () => {
        this.notFound = true;
        this.isLoading = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get fullName(): string {
    return `${this.profile?.firstName ?? ''} ${this.profile?.lastName ?? ''}`.trim();
  }

  get initials(): string {
    return `${this.profile?.firstName?.[0] ?? ''}${this.profile?.lastName?.[0] ?? ''}`.toUpperCase();
  }
}
