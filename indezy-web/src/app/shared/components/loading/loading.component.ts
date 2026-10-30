import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LogoComponent } from '../logo/logo.component';

@Component({
    selector: 'app-loading',
    imports: [
    MatProgressSpinnerModule,
    LogoComponent
],
    templateUrl: './loading.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./loading.component.scss']
})
export class LoadingComponent {
  @Input() message: string = '';
  @Input() subtitle: string = '';
  @Input() showLogo: boolean = true;
}
