import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ErrorDisplayComponent, ErrorDisplayConfig } from '../../../shared/components/error-display/error-display.component';

@Component({
    selector: 'app-server-error',
    imports: [
        CommonModule,
        RouterModule,
        ErrorDisplayComponent
    ],
    template: `
    <div class="server-error-container">
      <app-error-display 
        [config]="errorConfig"
        (retry)="onRetry()">
      </app-error-display>
    </div>
  `,
    styles: [`
    .server-error-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #ffeaa7 0%, #fab1a0 100%);
    }
  `]
})
export class ServerErrorComponent {
  errorConfig: ErrorDisplayConfig = {
    type: 'error',
    title: 'Erreur du serveur',
    message: 'Une erreur technique est survenue sur nos serveurs. Notre équipe a été notifiée et travaille à résoudre le problème.',
    showRetry: true,
    showHome: true,
    showLogo: true
  };

  constructor(private router: Router) {}

  onRetry(): void {
    // Reload the current page
    window.location.reload();
  }
}
