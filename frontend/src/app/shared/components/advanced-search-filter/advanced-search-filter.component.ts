import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged } from 'rxjs';

export interface SearchFilterConfig {
  placeholder?: string;
  label?: string;
  icon?: string;
  debounceTime?: number;
}

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
  template: `
    <mat-form-field appearance="outline" class="search-filter">
      <mat-label>{{ config.label || 'Rechercher' }}</mat-label>
      <input matInput
             [formControl]="searchForm.get('query')!"
             [placeholder]="config.placeholder || 'Tapez votre recherche...'">
      <mat-icon matPrefix *ngIf="config.icon">{{ config.icon }}</mat-icon>
      <button mat-icon-button 
              matSuffix 
              *ngIf="searchForm.get('query')?.value"
              (click)="clearSearch()"
              type="button">
        <mat-icon>clear</mat-icon>
      </button>
    </mat-form-field>
  `,
  styles: [`
    .search-filter {
      width: 100%;
      
      .mat-mdc-form-field-subscript-wrapper {
        display: none;
      }
    }
  `]
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
      this.searchChange.emit(value || '');
    });
  }

  clearSearch(): void {
    this.searchForm.get('query')?.setValue('');
  }
}
