import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-loading',
  standalone: true,
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  template: `
    <div class="loading-container" [class.fullscreen]="fullscreen">
      <div class="loading-content">
        <div class="loading-logo" *ngIf="showLogo">
          <img src="assets/images/indezy-logo.svg" alt="Indezy" class="logo-image">
        </div>
        
        <mat-spinner 
          [diameter]="spinnerSize" 
          [strokeWidth]="strokeWidth"
          [color]="color">
        </mat-spinner>
        
        <div class="loading-text" *ngIf="message">
          <p class="loading-message">{{ message }}</p>
          <p class="loading-subtitle" *ngIf="subtitle">{{ subtitle }}</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .loading-container {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 40px;
      min-height: 200px;

      &.fullscreen {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: rgba(255, 255, 255, 0.9);
        z-index: 9999;
        min-height: 100vh;
      }
    }

    .loading-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 20px;
      text-align: center;
    }

    .loading-logo {
      .logo-image {
        height: 48px;
        width: auto;
        opacity: 0.8;
        animation: pulse 2s ease-in-out infinite;
      }
    }

    .loading-text {
      .loading-message {
        margin: 0 0 8px 0;
        font-size: 16px;
        font-weight: 500;
        color: #333;
      }

      .loading-subtitle {
        margin: 0;
        font-size: 14px;
        color: #666;
      }
    }

    @keyframes pulse {
      0%, 100% {
        opacity: 0.8;
      }
      50% {
        opacity: 1;
      }
    }

    // Dark theme support
    @media (prefers-color-scheme: dark) {
      .loading-container.fullscreen {
        background-color: rgba(0, 0, 0, 0.9);
      }

      .loading-text {
        .loading-message {
          color: #fff;
        }

        .loading-subtitle {
          color: #ccc;
        }
      }
    }
  `]
})
export class LoadingComponent {
  @Input() message: string = 'Chargement...';
  @Input() subtitle: string = '';
  @Input() showLogo: boolean = true;
  @Input() fullscreen: boolean = false;
  @Input() spinnerSize: number = 50;
  @Input() strokeWidth: number = 4;
  @Input() color: 'primary' | 'accent' | 'warn' = 'primary';
}
