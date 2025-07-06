import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
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
