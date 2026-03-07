import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';
import { ErrorDisplayConfig } from '../../../models/ui.models';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

// Export the interface for use in other components
export { ErrorDisplayConfig } from '../../../models/ui.models';

@Component({
    selector: 'app-error-display',
    imports: [
        CommonModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        RouterModule,
        TranslateModule
    ],
    templateUrl: './error-display.component.html',
    styleUrls: ['./error-display.component.scss']
})
export class ErrorDisplayComponent {
  @Input() config: ErrorDisplayConfig = {
    type: 'error',
    showRetry: true,
    showHome: true,
    showLogo: true
  };
  
  @Output() retry = new EventEmitter<void>();

  constructor(private readonly translate: TranslateService) {}

  onRetry(): void {
    this.retry.emit();
  }

  getErrorIcon(): string {
    switch (this.config.type) {
      case 'warning':
        return 'warning';
      case 'info':
        return 'info';
      case 'not-found':
        return 'search_off';
      default:
        return 'error_outline';
    }
  }

  getDefaultTitle(): string {
    switch (this.config.type) {
      case 'warning':
        return this.translate.instant('errors.defaultWarningTitle');
      case 'info':
        return this.translate.instant('errors.defaultInfoTitle');
      case 'not-found':
        return this.translate.instant('errors.defaultNotFoundTitle');
      default:
        return this.translate.instant('errors.defaultErrorTitle');
    }
  }

  getDefaultMessage(): string {
    switch (this.config.type) {
      case 'warning':
        return this.translate.instant('errors.defaultWarningMessage');
      case 'info':
        return this.translate.instant('errors.defaultInfoMessage');
      case 'not-found':
        return this.translate.instant('errors.defaultNotFoundMessage');
      default:
        return this.translate.instant('errors.defaultErrorMessage');
    }
  }
}
