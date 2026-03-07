import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ErrorDisplayComponent, ErrorDisplayConfig } from '../../../shared/components/error-display/error-display.component';
import { TranslateService } from '@ngx-translate/core';

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
  errorConfig: ErrorDisplayConfig;

  constructor(private readonly router: Router, private readonly translate: TranslateService) {
    this.errorConfig = {
      type: 'error',
      title: this.translate.instant('errors.serverErrorTitle'),
      message: this.translate.instant('errors.serverErrorMessage'),
      showRetry: true,
      showHome: true,
      showLogo: true
    };
  }

  onRetry(): void {
    // Reload the current page
    globalThis.location.reload();
  }
}
