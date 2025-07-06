// User management related interfaces and types

export interface UserProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  avatar?: string;
  bio?: string;
  company?: string;
  position?: string;
  website?: string;
  linkedin?: string;
  github?: string;
  skills?: string[];
  languages?: string[];
  timezone?: string;
  currency?: string;
  notifications?: UserNotificationSettings;
  preferences?: UserPreferences;
}

export interface UserNotificationSettings {
  emailNotifications: boolean;
  pushNotifications: boolean;
  projectUpdates: boolean;
  clientMessages: boolean;
  systemAlerts: boolean;
  weeklyReports: boolean;
  marketingEmails: boolean;
}

export interface UserPreferences {
  theme: 'light' | 'dark' | 'auto';
  language: string;
  dateFormat: string;
  timeFormat: '12h' | '24h';
  currency: string;
  timezone: string;
  defaultView: 'dashboard' | 'projects' | 'clients';
  itemsPerPage: number;
  autoSave: boolean;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface UpdateUserProfileDto {
  firstName?: string;
  lastName?: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  avatar?: string;
  bio?: string;
  company?: string;
  position?: string;
  website?: string;
  linkedin?: string;
  github?: string;
  skills?: string[];
  languages?: string[];
  timezone?: string;
  currency?: string;
}

export interface UpdateNotificationSettingsDto {
  emailNotifications?: boolean;
  pushNotifications?: boolean;
  projectUpdates?: boolean;
  clientMessages?: boolean;
  systemAlerts?: boolean;
  weeklyReports?: boolean;
  marketingEmails?: boolean;
}

export interface UpdateUserPreferencesDto {
  theme?: 'light' | 'dark' | 'auto';
  language?: string;
  dateFormat?: string;
  timeFormat?: '12h' | '24h';
  currency?: string;
  timezone?: string;
  defaultView?: 'dashboard' | 'projects' | 'clients';
  itemsPerPage?: number;
  autoSave?: boolean;
}
