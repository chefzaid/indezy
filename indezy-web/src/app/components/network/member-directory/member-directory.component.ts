import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { MemberDirectory, NetworkAvailability } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MemberCardComponent } from '../member-card/member-card.component';

/** The member directory (`/network/members`): the members found by a skill, a city, an availability or remote work. */
@Component({
  selector: 'app-member-directory',
  imports: [PageHeaderComponent, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatPaginatorModule,
    MatProgressSpinnerModule, MatSelectModule, TranslateModule, MemberCardComponent],
  templateUrl: './member-directory.component.html',
  styleUrls: ['./member-directory.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MemberDirectoryComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly availabilities: Exclude<NetworkAvailability, 'AUTO'>[] = ['AVAILABLE', 'ON_MISSION', 'LISTENING'];
  readonly form = inject(FormBuilder).nonNullable.group({
    skill: '',
    city: '',
    availability: null as Exclude<NetworkAvailability, 'AUTO'> | null,
    remote: null as boolean | null
  });
  directory: MemberDirectory | null = null;
  notMember = false;
  loading = false;

  ngOnInit(): void {
    this.search();
  }

  search(page = 0): void {
    this.loading = true;
    this.network.directory({ ...this.form.getRawValue(), page }).subscribe({
      next: directory => {
        this.directory = directory;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.notMember = error.status === 409;
        if (!this.notMember) {
          this.notifications.error('network.directory.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  onPage(event: PageEvent): void {
    this.search(event.pageIndex);
  }
}
