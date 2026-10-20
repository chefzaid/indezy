import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  Output,
  inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { WorkMode } from '../../../models/project.models';
import {
  OFFER_WORK_MODES,
  OfferFacet,
  OfferFilters,
  OfferRegion,
  OfferSiteInfo,
  SENIORITIES,
  emptyOfferFilters
} from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { countActiveFilters } from '../../../services/offer/offer-query';
import { toIsoDate } from '../../../shared/locale/app-locale';

const TYPING_DELAY_MS = 400;
const MAX_SKILLS = 10;

/** The offer feed's filters; every change is emitted as a whole new set (typed fields after a pause). */
@Component({
  selector: 'app-offer-filters',
  imports: [FormsModule, MatAutocompleteModule, MatButtonModule, MatCheckboxModule, MatChipsModule, MatDatepickerModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './offer-filters.component.html',
  styleUrls: ['./offer-filters.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferFiltersComponent implements OnInit, OnChanges {
  @Input({ required: true }) filters: OfferFilters = emptyOfferFilters();
  @Input() regions: OfferRegion[] = [];
  @Input() sites: OfferSiteInfo[] = [];
  /** Whether the profile's city is known, so a distance can be chosen. */
  @Input() distanceAvailable = false;
  /** Whether the profile names skills, so offers can be kept by how well they match it. */
  @Input() matchAvailable = false;

  @Output() readonly filtersChange = new EventEmitter<OfferFilters>();

  readonly workModes = OFFER_WORK_MODES;
  readonly seniorities = SENIORITIES;
  readonly radiusOptions = [10, 25, 50, 100, 200];
  readonly remoteDayOptions = [1, 2, 3, 4];
  readonly durationOptions = [3, 6, 12, 24];
  readonly publishedOptions = [1, 3, 7, 30];
  readonly separatorKeys = [ENTER, COMMA];

  /** The values being edited; typed fields reach {@link filters} after a pause. */
  draft: OfferFilters = emptyOfferFilters();
  startBefore: Date | null = null;
  skillInput = '';
  skillSuggestions: OfferFacet[] = [];

  private readonly typing$ = new Subject<void>();
  private readonly skillPrefix$ = new Subject<string>();
  private readonly offerService = inject(OfferService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.typing$.pipe(debounceTime(TYPING_DELAY_MS), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.emit());
    this.skillPrefix$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(prefix => prefix.trim().length < 1 ? of([]) : this.offerService.skills(prefix.trim())),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: suggestions => this.skillSuggestions = suggestions.filter(s => !this.draft.skills.includes(s.value)),
      error: () => this.skillSuggestions = []
    });
  }

  ngOnChanges(): void {
    this.draft = { ...this.filters, regions: [...this.filters.regions], departments: [...this.filters.departments],
      workModes: [...this.filters.workModes], sites: [...this.filters.sites], seniorities: [...this.filters.seniorities],
      skills: [...this.filters.skills] };
    this.startBefore = this.filters.startBefore ? new Date(`${this.filters.startBefore}T00:00:00`) : null;
  }

  get activeCount(): number {
    return countActiveFilters(this.filters);
  }

  /** Text and number fields: wait for the user to stop typing. */
  typed(): void {
    this.typing$.next();
  }

  /** Lists, toggles and selects: apply at once. */
  changed(): void {
    this.emit();
  }

  toggleWorkMode(mode: WorkMode): void {
    const modes = this.draft.workModes;
    this.draft.workModes = modes.includes(mode) ? modes.filter(m => m !== mode) : [...modes, mode];
    this.emit();
  }

  dateChanged(date: Date | null): void {
    this.startBefore = date;
    this.draft.startBefore = toIsoDate(date);
    this.emit();
  }

  skillTyped(value: string): void {
    this.skillPrefix$.next(value);
  }

  addSkill(event: MatChipInputEvent): void {
    this.pushSkill(event.value);
    event.chipInput.clear();
  }

  pickSkill(event: MatAutocompleteSelectedEvent): void {
    this.pushSkill(event.option.value);
  }

  removeSkill(skill: string): void {
    this.draft.skills = this.draft.skills.filter(s => s !== skill);
    this.emit();
  }

  clear(): void {
    this.draft = emptyOfferFilters();
    this.startBefore = null;
    this.emit();
  }

  /** The chosen departments by name, for the closed select. */
  get departmentsSummary(): string {
    const names = new Map(this.regions.flatMap(region => region.departments).map(d => [d.departmentCode, d.name]));
    return this.draft.departments.map(code => names.get(code) ?? code).join(', ');
  }

  private pushSkill(raw: string): void {
    const skill = (raw ?? '').trim().slice(0, 60);
    const known = this.draft.skills.some(s => s.toLowerCase() === skill.toLowerCase());
    if (skill && !known && this.draft.skills.length < MAX_SKILLS) {
      this.draft.skills = [...this.draft.skills, skill];
      this.emit();
    }
    this.skillInput = '';
    this.skillSuggestions = [];
  }

  private emit(): void {
    const q = this.draft.q?.trim();
    this.filtersChange.emit({
      ...this.draft,
      q: q ? q : undefined,
      rateMin: positive(this.draft.rateMin),
      rateMax: positive(this.draft.rateMax),
      rateKnown: this.draft.rateKnown || undefined,
      unseenOnly: this.draft.unseenOnly || undefined,
      matchesProfile: (this.matchAvailable && this.draft.matchesProfile) || undefined,
      radiusKm: this.distanceAvailable ? this.draft.radiusKm : undefined
    });
  }
}

/** A whole number above zero, or nothing (an emptied number field gives null). */
function positive(value: number | null | undefined): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.round(value) : undefined;
}
