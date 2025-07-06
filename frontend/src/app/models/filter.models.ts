// Filter related interfaces and types

// Search Filter
export interface SearchFilterConfig {
  placeholder?: string;
  label?: string;
  icon?: string;
  debounceTime?: number;
}

// Date Range Filter
export interface DateRangeFilterConfig {
  fromLabel?: string;
  toLabel?: string;
  fromPlaceholder?: string;
  toPlaceholder?: string;
}

export interface DateRange {
  from: Date | null;
  to: Date | null;
}

// Multi Select Filter
export interface MultiSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface MultiSelectFilterConfig {
  label?: string;
  placeholder?: string;
  maxSelections?: number;
  allowCustomValues?: boolean;
}

// Range Slider Filter
export interface RangeSliderConfig {
  label?: string;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  showInputs?: boolean;
  debounceTime?: number;
}

export interface RangeValue {
  min: number;
  max: number;
}

// Filter Presets
export interface FilterPreset {
  id: string;
  name: string;
  description?: string;
  filters: Record<string, any>;
  isDefault?: boolean;
  createdAt?: Date;
}

export interface FilterPresetsConfig {
  allowCustomPresets?: boolean;
  maxPresets?: number;
  storageKey?: string;
}

// Comprehensive Filter Panel
export interface FilterSection {
  id: string;
  title: string;
  icon?: string;
  type: 'search' | 'dateRange' | 'multiSelect' | 'rangeSlider' | 'custom';
  config?: {
    label?: string;
    placeholder?: string;
    icon?: string;
    debounceTime?: number;
    fromLabel?: string;
    toLabel?: string;
    fromPlaceholder?: string;
    toPlaceholder?: string;
    maxSelections?: number;
    min?: number;
    max?: number;
    step?: number;
    unit?: string;
    showInputs?: boolean;
    [key: string]: string | number | boolean | undefined;
  };
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

// Filter Usage Example
export interface FilterValues {
  search?: string;
  industries?: string[];
  projectCount_min?: number;
  projectCount_max?: number;
  [key: string]: any;
}
