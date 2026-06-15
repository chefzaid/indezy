import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';

import { ReactiveFormsModule } from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatBadgeModule } from '@angular/material/badge';

import {
  AdvancedSearchFilterComponent,
  DateRangeFilterComponent,
  MultiSelectFilterComponent,
  RangeSliderFilterComponent,
  FilterPresetsComponent
} from '../index';
import {
  DateRange,
  RangeSliderConfig,
  RangeValue,
  FilterPreset,
  FilterSection,
  FilterValue,
  ComprehensiveFilterConfig
} from '../../../models/filter.models';

@Component({
  selector: 'app-comprehensive-filter-panel',
  standalone: true,
  imports: [
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
  templateUrl: './comprehensive-filter-panel.component.html',
  styleUrls: ['./comprehensive-filter-panel.component.scss']
})
export class ComprehensiveFilterPanelComponent implements OnInit {
  @Input() config: ComprehensiveFilterConfig = { sections: [] };
  @Input() initialFilters: Record<string, FilterValue> = {};
  @Output() filtersChange = new EventEmitter<Record<string, FilterValue>>();
  @Output() presetApplied = new EventEmitter<FilterPreset>();

  currentFilters: Record<string, FilterValue> = {};
  isExpanded: boolean = true;
  visibleSections: FilterSection[] = [];

  constructor() {
    // Component initialization
  }

  ngOnInit(): void {
    this.isExpanded = this.config.initiallyExpanded ?? true;
    this.currentFilters = { ...this.initialFilters };
    this.visibleSections = this.config.sections.filter(section => section.visible !== false);
  }

  onFilterChange(sectionId: string, value: string | number | string[] | boolean): void {
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

  getSearchValue(sectionId: string): string {
    return (this.currentFilters[sectionId] as string) ?? '';
  }

  getMultiSelectValues(sectionId: string): string[] {
    return (this.currentFilters[sectionId] as string[]) ?? [];
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
      from: (this.currentFilters[`${sectionId}_from`] as Date | null) ?? null,
      to: (this.currentFilters[`${sectionId}_to`] as Date | null) ?? null
    };
  }

  getRangeValue(sectionId: string): RangeValue {
    const section = this.config.sections.find(s => s.id === sectionId);
    const config = section?.config as RangeSliderConfig;

    return {
      min: (this.currentFilters[`${sectionId}_min`] as number) ?? config?.min ?? 0,
      max: (this.currentFilters[`${sectionId}_max`] as number) ?? config?.max ?? 100
    };
  }
}
