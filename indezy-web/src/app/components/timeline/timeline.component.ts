import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { AuthService } from '../../services/auth/auth.service';
import { InterviewStepDto, StepStatus } from '../../models';
import { LoadingComponent } from '../../shared/components/loading/loading.component';

/**
 * Chronological view of every scheduled (PLANNED) interview step across all of the
 * freelancer's opportunities, soonest first.
 */
@Component({
  selector: 'app-timeline',
  imports: [CommonModule, RouterModule, MatCardModule, MatIconModule, TranslateModule, LoadingComponent],
  templateUrl: './timeline.component.html',
  styleUrls: ['./timeline.component.scss']
})
export class TimelineComponent implements OnInit {
  upcomingSteps: InterviewStepDto[] = [];
  isLoading = false;

  constructor(
    private readonly interviewStepService: InterviewStepService,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    const freelanceId = this.authService.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    this.isLoading = true;
    this.interviewStepService.getByFreelanceIdAndStatus(freelanceId, StepStatus.PLANNED).subscribe({
      next: steps => {
        this.upcomingSteps = this.toUpcoming(steps);
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  /** Keeps only steps with a future date, ordered soonest first. */
  toUpcoming(steps: InterviewStepDto[]): InterviewStepDto[] {
    const now = Date.now();
    return steps
      .filter(step => !!step.date && new Date(step.date).getTime() >= now)
      .sort((a, b) => new Date(a.date as string).getTime() - new Date(b.date as string).getTime());
  }
}
