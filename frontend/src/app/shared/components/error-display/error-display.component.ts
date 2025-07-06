import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';
import { ErrorDisplayConfig } from '../../../models/ui.models';

// Export the interface for use in other components
export { ErrorDisplayConfig } from '../../../models/ui.models';

@Component({
    selector: 'app-error-display',
    imports: [
        CommonModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        RouterModule
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
        return 'Attention';
      case 'info':
        return 'Information';
      case 'not-found':
        return 'Page non trouvée';
      default:
        return 'Une erreur est survenue';
    }
  }

  getDefaultMessage(): string {
    switch (this.config.type) {
      case 'warning':
        return 'Veuillez vérifier les informations saisies.';
      case 'info':
        return 'Voici une information importante.';
      case 'not-found':
        return 'La page que vous recherchez n\'existe pas ou a été déplacée.';
      default:
        return 'Nous nous excusons pour la gêne occasionnée. Veuillez réessayer.';
    }
  }
}
