import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatBadgeModule } from '@angular/material/badge';

import { 
  AdvancedSearchFilterComponent, 
  DateRangeFilterComponent,
  DateRange,
  MultiSelectFilterComponent,
  MultiSelectOption,
  RangeSliderFilterComponent,
  RangeSliderConfig,
  RangeValue,
  FilterPresetsComponent,
  FilterPresetsConfig,
  FilterPreset
} from '../index';

export interface FilterSection {
  id: string;
  title: string;
  icon?: string;
  type: 'search' | 'dateRange' | 'multiSelect' | 'rangeSlider' | 'custom';
  config?: any;
  options?: MultiSelectOption[];
  visible?: boolean;
  required?: boolean;
}

export interface ComprehensiveFilterConfig {
  title?: string;
  subtitle?: string;
  sections: FilterSection[];
  showPresets?: boolean;
  presetsConfig?: FilterPresetsConfig;
  collapsible?: boolean;
  initiallyExpanded?: boolean;
}

@Component({
  selector: 'app-comprehensive-filter-panel',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatExpansionModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatBadgeModule,
    AdvancedSearchFilterComponent,
    DateRangeFilterComponent,
    MultiSelectFilterComponent,
    RangeSliderFilterComponent,
    FilterPresetsComponent
  ],
  template: `
    <div class="comprehensive-filter-panel">
      <div class="filter-header">
        <div class="filter-title">
          <h3>{{ config.title || 'Filtres' }}</h3>
          <p *ngIf="config.subtitle" class="filter-subtitle">{{ config.subtitle }}</p>
        </div>
        
        <div class="filter-actions">
          <app-filter-presets
            *ngIf="config.showPresets"
            [config]="config.presetsConfig || {}"
            [currentFilters]="currentFilters"
            [hasActiveFilters]="getActiveFilterCount() > 0"
            (presetApplied)="onPresetApplied($event)"
            (presetSaved)="onPresetSaved($event)"
            (presetDeleted)="onPresetDeleted($event)">
          </app-filter-presets>

          <button mat-button 
                  *ngIf="getActiveFilterCount() > 0"
                  (click)="clearAllFilters()"
                  class="clear-all-button">
            <mat-icon>clear_all</mat-icon>
            Tout effacer
          </button>

          <button mat-icon-button 
                  *ngIf="config.collapsible"
                  (click)="toggleExpanded()"
                  [matBadge]="getActiveFilterCount()"
                  [matBadgeHidden]="getActiveFilterCount() === 0"
                  matBadgeColor="primary"
                  matBadgeSize="small">
            <mat-icon>{{ isExpanded ? 'expand_less' : 'expand_more' }}</mat-icon>
          </button>
        </div>
      </div>

      <div class="filter-content" [class.collapsed]="config.collapsible && !isExpanded">
        <div class="filter-sections">
          <div *ngFor="let section of visibleSections; let last = last" 
               class="filter-section">
            
            <div class="section-header" *ngIf="section.title">
              <mat-icon *ngIf="section.icon">{{ section.icon }}</mat-icon>
              <span class="section-title">{{ section.title }}</span>
            </div>

            <div class="section-content">
              <!-- Search Filter -->
              <app-advanced-search-filter
                *ngIf="section.type === 'search'"
                [config]="section.config || {}"
                [initialValue]="currentFilters[section.id] || ''"
                (searchChange)="onFilterChange(section.id, $event)">
              </app-advanced-search-filter>

              <!-- Date Range Filter -->
              <app-date-range-filter
                *ngIf="section.type === 'dateRange'"
                [config]="section.config || {}"
                [initialRange]="getDateRange(section.id)"
                (rangeChange)="onDateRangeChange(section.id, $event)">
              </app-date-range-filter>

              <!-- Multi Select Filter -->
              <app-multi-select-filter
                *ngIf="section.type === 'multiSelect'"
                [options]="section.options || []"
                [config]="section.config || {}"
                [initialValues]="currentFilters[section.id] || []"
                (selectionChange)="onFilterChange(section.id, $event)">
              </app-multi-select-filter>

              <!-- Range Slider Filter -->
              <app-range-slider-filter
                *ngIf="section.type === 'rangeSlider'"
                [config]="section.config || {}"
                [initialRange]="getRangeValue(section.id)"
                (rangeChange)="onRangeChange(section.id, $event)">
              </app-range-slider-filter>

              <!-- Custom Content Slot -->
              <ng-content 
                *ngIf="section.type === 'custom'"
                [select]="'[slot=' + section.id + ']'">
              </ng-content>
            </div>

            <mat-divider *ngIf="!last"></mat-divider>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .comprehensive-filter-panel {
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      background-color: #fafafa;
      overflow: hidden;

      .filter-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px 20px;
        background-color: white;
        border-bottom: 1px solid #e0e0e0;

        .filter-title {
          h3 {
            margin: 0;
            font-size: 18px;
            font-weight: 500;
            color: #333;
          }

          .filter-subtitle {
            margin: 4px 0 0 0;
            font-size: 14px;
            color: #666;
          }
        }

        .filter-actions {
          display: flex;
          align-items: center;
          gap: 8px;

          .clear-all-button {
            color: #f44336;
          }
        }
      }

      .filter-content {
        transition: all 0.3s ease;
        overflow: hidden;

        &.collapsed {
          max-height: 0;
          padding: 0;
        }

        .filter-sections {
          padding: 20px;

          .filter-section {
            margin-bottom: 24px;

            &:last-child {
              margin-bottom: 0;
            }

            .section-header {
              display: flex;
              align-items: center;
              gap: 8px;
              margin-bottom: 12px;

              mat-icon {
                color: #666;
                font-size: 20px;
                width: 20px;
                height: 20px;
              }

              .section-title {
                font-weight: 500;
                color: #333;
                font-size: 14px;
                text-transform: uppercase;
                letter-spacing: 0.5px;
              }
            }

            .section-content {
              padding-left: 28px;
            }

            mat-divider {
              margin-top: 16px;
            }
          }
        }
      }

      @media (max-width: 768px) {
        .filter-header {
          flex-direction: column;
          align-items: stretch;
          gap: 12px;

          .filter-actions {
            justify-content: space-between;
          }
        }

        .filter-content {
          .filter-sections {
            padding: 16px;

            .filter-section {
              .section-content {
                padding-left: 0;
              }
            }
          }
        }
      }
    }
  `]
})
export class ComprehensiveFilterPanelComponent implements OnInit {
  @Input() config: ComprehensiveFilterConfig = { sections: [] };
  @Input() initialFilters: any = {};
  @Output() filtersChange = new EventEmitter<any>();
  @Output() presetApplied = new EventEmitter<FilterPreset>();

  currentFilters: any = {};
  isExpanded: boolean = true;
  visibleSections: FilterSection[] = [];

  constructor() {}

  ngOnInit(): void {
    this.isExpanded = this.config.initiallyExpanded ?? true;
    this.currentFilters = { ...this.initialFilters };
    this.visibleSections = this.config.sections.filter(section => section.visible !== false);
  }

  onFilterChange(sectionId: string, value: any): void {
    this.currentFilters[sectionId] = value;
    this.filtersChange.emit({ ...this.currentFilters });
  }

  onDateRangeChange(sectionId: string, range: DateRange): void {
    this.currentFilters[`${sectionId}_from`] = range.from;
    this.currentFilters[`${sectionId}_to`] = range.to;
    this.filtersChange.emit({ ...this.currentFilters });
  }

  onRangeChange(sectionId: string, range: RangeValue): void {
    this.currentFilters[`${sectionId}_min`] = range.min;
    this.currentFilters[`${sectionId}_max`] = range.max;
    this.filtersChange.emit({ ...this.currentFilters });
  }

  onPresetApplied(preset: FilterPreset): void {
    this.currentFilters = { ...preset.filters };
    this.filtersChange.emit({ ...this.currentFilters });
    this.presetApplied.emit(preset);
  }

  onPresetSaved(preset: FilterPreset): void {
    // Handle preset saved event if needed
  }

  onPresetDeleted(preset: FilterPreset): void {
    // Handle preset deleted event if needed
  }

  clearAllFilters(): void {
    this.currentFilters = {};
    this.filtersChange.emit({});
  }

  toggleExpanded(): void {
    this.isExpanded = !this.isExpanded;
  }

  getActiveFilterCount(): number {
    return Object.keys(this.currentFilters).filter(key => {
      const value = this.currentFilters[key];
      return value !== null && value !== undefined && value !== '' && 
             (Array.isArray(value) ? value.length > 0 : true);
    }).length;
  }

  getDateRange(sectionId: string): DateRange {
    return {
      from: this.currentFilters[`${sectionId}_from`] ?? null,
      to: this.currentFilters[`${sectionId}_to`] ?? null
    };
  }

  getRangeValue(sectionId: string): RangeValue {
    const section = this.config.sections.find(s => s.id === sectionId);
    const config = section?.config as RangeSliderConfig;
    
    return {
      min: this.currentFilters[`${sectionId}_min`] ?? config?.min ?? 0,
      max: this.currentFilters[`${sectionId}_max`] ?? config?.max ?? 100
    };
  }
}
