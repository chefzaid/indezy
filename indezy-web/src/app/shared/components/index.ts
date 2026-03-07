// Advanced Filter Components
export * from './advanced-search-filter/advanced-search-filter.component';
export * from './date-range-filter/date-range-filter.component';
export * from './multi-select-filter/multi-select-filter.component';
export * from './range-slider-filter/range-slider-filter.component';
export * from './filter-presets/filter-presets.component';
export * from './comprehensive-filter-panel/comprehensive-filter-panel.component';

// UI Components
export * from './loading/loading.component';
export * from './error-display/error-display.component';

// Re-export all components as an array for easy importing
import { AdvancedSearchFilterComponent } from './advanced-search-filter/advanced-search-filter.component';
import { DateRangeFilterComponent } from './date-range-filter/date-range-filter.component';
import { MultiSelectFilterComponent } from './multi-select-filter/multi-select-filter.component';
import { RangeSliderFilterComponent } from './range-slider-filter/range-slider-filter.component';
import { FilterPresetsComponent } from './filter-presets/filter-presets.component';
import { ComprehensiveFilterPanelComponent } from './comprehensive-filter-panel/comprehensive-filter-panel.component';
import { LoadingComponent } from './loading/loading.component';
import { ErrorDisplayComponent } from './error-display/error-display.component';

export const SHARED_FILTER_COMPONENTS = [
  AdvancedSearchFilterComponent,
  DateRangeFilterComponent,
  MultiSelectFilterComponent,
  RangeSliderFilterComponent,
  FilterPresetsComponent,
  ComprehensiveFilterPanelComponent
];

export const SHARED_UI_COMPONENTS = [
  LoadingComponent,
  ErrorDisplayComponent
];

export const ALL_SHARED_COMPONENTS = [
  ...SHARED_FILTER_COMPONENTS,
  ...SHARED_UI_COMPONENTS
];
