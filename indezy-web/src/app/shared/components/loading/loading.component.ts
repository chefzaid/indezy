import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

@Component({
    selector: 'app-loading',
    imports: [
        CommonModule,
        MatProgressSpinnerModule,
        MatIconModule
    ],
    templateUrl: './loading.component.html',
    styleUrls: ['./loading.component.scss']
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
