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
import { ComprehensiveFilterPanelComponent } from '../../../shared/components';
import { ComprehensiveFilterConfig } from '../../../models/filter.models';

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
    TranslateModule,
    ComprehensiveFilterPanelComponent
  ],
  templateUrl: './source-list.component.html',
  styleUrls: ['./source-list.component.scss']
})
export class SourceListComponent implements OnInit, OnDestroy {
  sources: SourceDto[] = [];
  filteredSources: SourceDto[] = [];
  isLoading = false;
  displayedColumns: string[] = ['name', 'type', 'link', 'popularityRating', 'usefulnessRating', 'actions'];

  filterConfig: ComprehensiveFilterConfig = { sections: [] };
  private activeFilters: Record<string, unknown> = {};

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly sourceService: SourceService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.buildFilterConfig();
    this.loadSources();
  }

  private buildFilterConfig(): void {
    const t = (key: string): string => this.translate.instant(key);
    this.filterConfig = {
      title: t('common.filters'),
      collapsible: true,
      initiallyExpanded: false,
      sections: [
        {
          id: 'search',
          type: 'search',
          title: t('common.search'),
          icon: 'search',
          config: { placeholder: t('sources.namePlaceholder') }
        },
        {
          id: 'type',
          type: 'multiSelect',
          title: t('common.type'),
          icon: 'category',
          config: { placeholder: t('common.type') },
          options: this.sourceService.getSourceTypes().map(type => ({
            value: type,
            label: this.getTypeLabel(type)
          }))
        },
        {
          id: 'popularity',
          type: 'rangeSlider',
          title: t('sources.popularity'),
          icon: 'star',
          config: { min: 0, max: 5, step: 1, unit: '★', showInputs: false }
        },
        {
          id: 'usefulness',
          type: 'rangeSlider',
          title: t('sources.usefulness'),
          icon: 'recommend',
          config: { min: 0, max: 5, step: 1, unit: '★', showInputs: false }
        }
      ]
    };
  }

  onFiltersChange(filters: Record<string, unknown>): void {
    this.activeFilters = filters;
    this.applyFilters();
  }

  private applyFilters(): void {
    const f = this.activeFilters;
    const search = ((f['search'] as string) ?? '').trim().toLowerCase();
    const types = (f['type'] as string[]) ?? [];
    const popMin = f['popularity_min'] as number | undefined;
    const popMax = f['popularity_max'] as number | undefined;
    const useMin = f['usefulness_min'] as number | undefined;
    const useMax = f['usefulness_max'] as number | undefined;

    this.filteredSources = this.sources.filter(source =>
      this.matchesSearch(source, search) &&
      this.matchesType(source, types) &&
      this.inRange(source.popularityRating, popMin, popMax) &&
      this.inRange(source.usefulnessRating, useMin, useMax)
    );
  }

  private matchesSearch(source: SourceDto, search: string): boolean {
    if (!search) {return true;}
    const haystack = [source.name, source.notes, source.link].filter(Boolean).join(' ').toLowerCase();
    return haystack.includes(search);
  }

  private matchesType(source: SourceDto, types: string[]): boolean {
    return types.length === 0 || types.includes(source.type);
  }

  private inRange(value: number | undefined, min?: number, max?: number): boolean {
    if (min === undefined && max === undefined) {return true;}
    const rating = value ?? 0;
    if (min !== undefined && rating < min) {return false;}
    if (max !== undefined && rating > max) {return false;}
    return true;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadSources(): void {
    this.isLoading = true;
    const user = this.authService.getUser();
    if (!user?.id) {return;}

    this.sourceService.getByFreelanceId(user.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (sources) => {
          this.sources = sources;
          this.applyFilters();
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
    if (!source.id) {return;}
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
    if (!rating) {return '-';}
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }
}
