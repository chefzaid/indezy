import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';

export interface ErrorDisplayConfig {
  title?: string;
  message?: string;
  icon?: string;
  showRetry?: boolean;
  showHome?: boolean;
  showLogo?: boolean;
  type?: 'error' | 'warning' | 'info' | 'not-found';
}

@Component({
    selector: 'app-error-display',
    imports: [
        CommonModule,
        MatButtonModule,
        MatIconModule,
        MatCardModule,
        RouterModule
    ],
    template: `
    <div class="error-container">
      <mat-card class="error-card">
        <mat-card-content>
          <div class="error-content">
            <div class="error-logo" *ngIf="config.showLogo">
              <img src="assets/images/indezy-logo.svg" alt="Indezy" class="logo-image">
            </div>
            
            <div class="error-icon" [class]="'error-' + config.type">
              <mat-icon>{{ getErrorIcon() }}</mat-icon>
            </div>
            
            <div class="error-text">
              <h2 class="error-title">{{ config.title || getDefaultTitle() }}</h2>
              <p class="error-message">{{ config.message || getDefaultMessage() }}</p>
            </div>
            
            <div class="error-actions">
              <button mat-raised-button 
                      color="primary" 
                      *ngIf="config.showRetry"
                      (click)="onRetry()">
                <mat-icon>refresh</mat-icon>
                Réessayer
              </button>
              
              <button mat-button 
                      *ngIf="config.showHome"
                      routerLink="/dashboard">
                <mat-icon>home</mat-icon>
                Retour à l'accueil
              </button>
            </div>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
    styles: [`
    .error-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 400px;
      padding: 20px;
    }

    .error-card {
      max-width: 500px;
      width: 100%;
      text-align: center;
    }

    .error-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 20px;
      padding: 20px;
    }

    .error-logo {
      .logo-image {
        height: 40px;
        width: auto;
        opacity: 0.7;
      }
    }

    .error-icon {
      mat-icon {
        font-size: 4rem;
        width: 4rem;
        height: 4rem;
      }

      &.error-error mat-icon {
        color: #f44336;
      }

      &.error-warning mat-icon {
        color: #ff9800;
      }

      &.error-info mat-icon {
        color: #2196f3;
      }

      &.error-not-found mat-icon {
        color: #9e9e9e;
      }
    }

    .error-text {
      .error-title {
        margin: 0 0 12px 0;
        font-size: 24px;
        font-weight: 500;
        color: #333;
      }

      .error-message {
        margin: 0;
        font-size: 16px;
        color: #666;
        line-height: 1.5;
      }
    }

    .error-actions {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      justify-content: center;
    }

    // Mobile responsive
    @media (max-width: 480px) {
      .error-container {
        padding: 10px;
        min-height: 300px;
      }

      .error-content {
        padding: 16px;
        gap: 16px;
      }

      .error-icon mat-icon {
        font-size: 3rem;
        width: 3rem;
        height: 3rem;
      }

      .error-text .error-title {
        font-size: 20px;
      }

      .error-actions {
        flex-direction: column;
        width: 100%;

        button {
          width: 100%;
        }
      }
    }

    // Dark theme support
    @media (prefers-color-scheme: dark) {
      .error-text {
        .error-title {
          color: #fff;
        }

        .error-message {
          color: #ccc;
        }
      }
    }
  `]
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
