import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { SourceService } from '../../../services/source/source.service';
import { AuthService } from '../../../services/auth/auth.service';
import { SourceDto, SourceType } from '../../../models/source.models';

@Component({
  selector: 'app-source-list',
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './source-list.component.html',
  styleUrls: ['./source-list.component.scss']
})
export class SourceListComponent implements OnInit, OnDestroy {
  sources: SourceDto[] = [];
  isLoading = false;
  displayedColumns: string[] = ['name', 'type', 'link', 'popularityRating', 'usefulnessRating', 'actions'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly sourceService: SourceService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadSources();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadSources(): void {
    this.isLoading = true;
    const user = this.authService.getUser();
    if (!user?.id) return;

    this.sourceService.getByFreelanceId(user.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (sources) => {
          this.sources = sources;
          this.isLoading = false;
        },
        error: () => {
          this.snackBar.open(this.translate.instant('errors.loadingSources'), this.translate.instant('common.close'), { duration: 3000 });
          this.isLoading = false;
        }
      });
  }

  onCreate(): void {
    this.router.navigate(['/sources/create']);
  }

  onEdit(source: SourceDto): void {
    this.router.navigate(['/sources', source.id, 'edit']);
  }

  onDelete(source: SourceDto): void {
    if (!source.id) return;
    if (confirm(this.translate.instant('sources.confirmDelete', { name: source.name }))) {
      this.sourceService.delete(source.id)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open(this.translate.instant('sources.deleteSuccess'), 'OK', { duration: 2000 });
            this.loadSources();
          },
          error: () => {
            this.snackBar.open(this.translate.instant('errors.deletingSource'), this.translate.instant('common.close'), { duration: 3000 });
          }
        });
    }
  }

  getTypeLabel(type: SourceType): string {
    return this.sourceService.getSourceTypeLabel(type);
  }

  getTypeIcon(type: SourceType): string {
    const icons: Record<SourceType, string> = {
      'JOB_BOARD': 'work',
      'SOCIAL_MEDIA': 'share',
      'EMAIL': 'email',
      'CALL': 'phone',
      'SMS': 'sms'
    };
    return icons[type] || 'source';
  }

  getRatingDisplay(rating?: number): string {
    if (!rating) return '-';
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }
}
