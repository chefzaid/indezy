import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { RateBenchmarkService, RateFigure, RateRegion, RateSeniority, RateSkills } from '../../../services/rate-benchmark/rate-benchmark.service';

/**
 * The network's rate benchmark, beside the feed: a skill backed by enough freelancers, for a seniority and a region or all
 * of them, gives the median daily rate and the middle half; a figure fewer than five freelancers back stays hidden.
 */
@Component({
  selector: 'app-rate-benchmark-card',
  imports: [DecimalPipe, RouterLink, MatFormFieldModule, MatSelectModule, TranslateModule],
  templateUrl: './rate-benchmark-card.component.html',
  styleUrls: ['./rate-benchmark-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class RateBenchmarkCardComponent implements OnInit {
  private readonly benchmark = inject(RateBenchmarkService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly seniorities: RateSeniority[] = ['JUNIOR', 'CONFIRMED', 'SENIOR', 'EXPERT'];
  readonly regions: RateRegion[] = ['ILE_DE_FRANCE', 'REGIONS', 'REMOTE'];
  skills: RateSkills | null = null;
  skill: string | null = null;
  seniority: RateSeniority | null = null;
  region: RateRegion | null = null;
  figure: RateFigure | null = null;

  ngOnInit(): void {
    this.benchmark.skills().subscribe({
      next: skills => {
        this.skills = Array.isArray(skills?.skills) ? skills : null;
        this.skill = this.skills?.skills[0]?.skill ?? null;
        this.load();
      },
      error: () => undefined
    });
  }

  choose(change: { skill?: string; seniority?: RateSeniority | null; region?: RateRegion | null }): void {
    if (change.skill !== undefined) {
      this.skill = change.skill;
    }
    if (change.seniority !== undefined) {
      this.seniority = change.seniority;
    }
    if (change.region !== undefined) {
      this.region = change.region;
    }
    this.load();
  }

  private load(): void {
    if (!this.skill) {
      this.changes.markForCheck();
      return;
    }
    this.benchmark.figure(this.skill, this.seniority, this.region).subscribe({
      next: figure => {
        this.figure = figure;
        this.changes.markForCheck();
      },
      error: () => this.figure = null
    });
  }
}
