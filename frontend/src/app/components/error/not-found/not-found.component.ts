import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ErrorDisplayComponent, ErrorDisplayConfig } from '../../../shared/components/error-display/error-display.component';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ErrorDisplayComponent
  ],
  template: `
    <div class="not-found-container">
      <app-error-display 
        [config]="errorConfig"
        (retry)="goHome()">
      </app-error-display>
    </div>
  `,
  styles: [`
    .not-found-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
    }
  `]
})
export class NotFoundComponent {
  errorConfig: ErrorDisplayConfig = {
    type: 'not-found',
    title: '404 - Page non trouvée',
    message: 'La page que vous recherchez n\'existe pas ou a été déplacée. Retournez à l\'accueil pour continuer à utiliser Indezy.',
    showRetry: false,
    showHome: true,
    showLogo: true
  };

  goHome(): void {
    // This method is called when retry is clicked, but we don't show retry for 404
    // It's here for interface compliance
  }
}
