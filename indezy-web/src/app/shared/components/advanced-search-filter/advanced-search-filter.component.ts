import { Component, DestroyRef, Input, Output, EventEmitter, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormControl } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { SearchFilterConfig } from '../../../models/filter.models';

@Component({
  selector: 'app-advanced-search-filter',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule
  ],
  templateUrl: './advanced-search-filter.component.html',
  styleUrls: ['./advanced-search-filter.component.scss']
})
export class AdvancedSearchFilterComponent implements OnInit {
  @Input() config: SearchFilterConfig = {};
  @Input() initialValue: string = '';
  @Output() searchChange = new EventEmitter<string>();

  searchForm: FormGroup;

  get queryControl(): FormControl {
    return this.searchForm.get('query') as FormControl;
  }

  private readonly destroyRef = inject(DestroyRef);
  constructor(private readonly fb: FormBuilder) {
    this.searchForm = this.fb.group({
      query: ['']
    });
  }

  ngOnInit(): void {
    // Set initial value
    if (this.initialValue) {
      this.searchForm.get('query')?.setValue(this.initialValue);
    }

    // Setup debounced search
    this.searchForm.get('query')?.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef),
      debounceTime(this.config.debounceTime || 300),
      distinctUntilChanged()
    ).subscribe(value => {
      this.searchChange.emit(value ?? '');
    });
  }

  clearSearch(): void {
    this.searchForm.get('query')?.setValue('');
  }
}
