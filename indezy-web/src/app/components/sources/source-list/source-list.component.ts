import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';

import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { SourceService } from '../../../services/source/source.service';
import { AuthService } from '../../../services/auth/auth.service';
import { SourceDto, SourceType } from '../../../models/source.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

@Component({
  selector: 'app-source-list',
  imports: [
    RouterModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule
  ],
  templateUrl: './source-list.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./source-list.component.scss']
})
export class SourceListComponent implements OnInit, OnDestroy {
  sources: SourceDto[] = [];
  filteredSources: SourceDto[] = [];
  isLoading = false;
  displayedColumns: string[] = ['name', 'type', 'link', 'popularityRating', 'usefulnessRating', 'actions'];

  readonly sourceTypes = this.sourceService.getSourceTypes();
  searchQuery = '';
  typeFilter: SourceType | '' = '';
  /** Minimum usefulness rating (0 keeps every source). */
  minRating = 0;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly sourceService: SourceService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.loadSources();
  }

  applyFilters(): void {
    const search = this.searchQuery.trim().toLowerCase();
    this.filteredSources = this.sources.filter(source =>
      this.matchesSearch(source, search) &&
      (!this.typeFilter || source.type === this.typeFilter) &&
      (source.usefulnessRating ?? 0) >= this.minRating
    );
  }

  clearFilters(): void {
    this.searchQuery = '';
    this.typeFilter = '';
    this.minRating = 0;
    this.applyFilters();
  }

  private matchesSearch(source: SourceDto, search: string): boolean {
    if (!search) {return true;}
    const haystack = [source.name, source.notes, source.link].filter(Boolean).join(' ').toLowerCase();
    return haystack.includes(search);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadSources(): void {
    const user = this.authService.getUser();
    if (!user?.id) {return;}
    this.isLoading = true;

    this.sourceService.getByFreelanceId(user.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (sources) => {
          this.sources = sources;
          this.applyFilters();
          this.isLoading = false;
        },
        error: () => {
          this.notificationService.error('errors.loadingSources');
          this.isLoading = false;
        }
      });
  }

  onCreate(): void {
    this.router.navigate(['/sources/new']);
  }

  onEdit(source: SourceDto): void {
    this.router.navigate(['/sources', source.id, 'edit']);
  }

  onDelete(source: SourceDto): void {
    if (!source.id) {return;}
    const sourceId = source.id;
    this.confirmDialog.confirm({
      messageKey: 'sources.confirmDelete',
      messageParams: { name: source.name },
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.sourceService.delete(sourceId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.notificationService.success('sources.deleteSuccess', 2000);
            this.loadSources();
          },
          error: () => {
            this.notificationService.error('errors.deletingSource');
          }
        });
    });
  }

  getTypeLabel(type: SourceType): string {
    return this.translate.instant('sources.types.' + type);
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
    if (!rating) {return '-';}
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }
}
