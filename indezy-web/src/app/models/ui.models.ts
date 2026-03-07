// UI related interfaces and types

// Error Display
export interface ErrorDisplayConfig {
  title?: string;
  message?: string;
  icon?: string;
  showRetry?: boolean;
  showHome?: boolean;
  showLogo?: boolean;
  type?: 'error' | 'warning' | 'info' | 'not-found';
}

// Loading states
export interface LoadingState {
  isLoading: boolean;
  message?: string;
  progress?: number;
}

// Common UI types
export type ThemeMode = 'light' | 'dark' | 'auto';
export type ViewMode = 'list' | 'grid' | 'card';
export type SortDirection = 'asc' | 'desc';

export interface SortConfig {
  field: string;
  direction: SortDirection;
}

export interface PaginationConfig {
  page: number;
  size: number;
  total: number;
}

// Navigation
export interface NavigationItem {
  id: string;
  label: string;
  icon?: string;
  route?: string;
  children?: NavigationItem[];
  disabled?: boolean;
  badge?: string | number;
}

// Form validation
export interface ValidationError {
  field: string;
  message: string;
  code?: string;
}

export interface FormState {
  isValid: boolean;
  isDirty: boolean;
  isSubmitting: boolean;
  errors: ValidationError[];
}
