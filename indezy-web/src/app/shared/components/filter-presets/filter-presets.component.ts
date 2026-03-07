import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FilterPreset, FilterPresetsConfig } from '../../../models/filter.models';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-filter-presets',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    MatDialogModule,
    MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './filter-presets.component.html',
  styleUrls: ['./filter-presets.component.scss']
})
export class FilterPresetsComponent implements OnInit {
  @Input() config: FilterPresetsConfig = {};
  @Input() currentFilters: Record<string, any> = {};
  @Input() hasActiveFilters: boolean = false;
  @Output() presetApplied = new EventEmitter<FilterPreset>();
  @Output() presetSaved = new EventEmitter<FilterPreset>();
  @Output() presetDeleted = new EventEmitter<FilterPreset>();

  presets: FilterPreset[] = [];
  customPresets: FilterPreset[] = [];

  constructor(
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadPresets();
  }

  applyPreset(preset: FilterPreset): void {
    this.presetApplied.emit(preset);
    this.snackBar.open(this.translate.instant('filters.applied', { name: preset.name }), this.translate.instant('common.close'), {
      duration: 2000
    });
  }

  saveCurrentAsPreset(): void {
    const name = prompt(this.translate.instant('filters.presetNamePrompt'));
    if (name?.trim()) {
      const preset: FilterPreset = {
        id: this.generateId(),
        name: name.trim(),
        filters: { ...this.currentFilters },
        createdAt: new Date()
      };

      this.customPresets.push(preset);
      this.savePresets();
      this.loadPresets();
      
      this.presetSaved.emit(preset);
      this.snackBar.open(this.translate.instant('filters.saved', { name: preset.name }), this.translate.instant('common.close'), {
        duration: 2000
      });
    }
  }

  deletePreset(preset: FilterPreset, event: Event): void {
    event.stopPropagation();
    
    if (confirm(this.translate.instant('filters.confirmDelete', { name: preset.name }))) {
      const index = this.customPresets.findIndex(p => p.id === preset.id);
      if (index >= 0) {
        this.customPresets.splice(index, 1);
        this.savePresets();
        this.loadPresets();
        
        this.presetDeleted.emit(preset);
        this.snackBar.open(this.translate.instant('filters.deleted', { name: preset.name }), this.translate.instant('common.close'), {
          duration: 2000
        });
      }
    }
  }

  clearAllPresets(): void {
    if (confirm(this.translate.instant('filters.confirmClearAll'))) {
      this.customPresets = [];
      this.savePresets();
      this.loadPresets();
      
      this.snackBar.open(this.translate.instant('filters.allCleared'), this.translate.instant('common.close'), {
        duration: 2000
      });
    }
  }

  private loadPresets(): void {
    // Load default presets
    const defaultPresets: FilterPreset[] = [
      {
        id: 'active-clients',
        name: this.translate.instant('filters.defaults.activeClients'),
        description: this.translate.instant('filters.defaults.activeClientsDesc'),
        filters: { status: 'ACTIVE' },
        isDefault: true
      },
      {
        id: 'recent-clients',
        name: this.translate.instant('filters.defaults.recentClients'),
        description: this.translate.instant('filters.defaults.recentClientsDesc'),
        filters: { 
          createdDateFrom: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
        },
        isDefault: true
      },
      {
        id: 'prospects',
        name: this.translate.instant('filters.defaults.esn'),
        description: this.translate.instant('filters.defaults.esnDesc'),
        filters: { status: 'ESN' },
        isDefault: true
      }
    ];

    // Load custom presets from storage
    if (this.config.allowCustomPresets) {
      const storageKey = this.config.storageKey ?? 'filter-presets';
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        try {
          this.customPresets = JSON.parse(stored);
        } catch (e) {
          console.error('Error loading presets:', e);
          this.customPresets = [];
        }
      }
    }

    this.presets = [...defaultPresets, ...this.customPresets];
  }

  private savePresets(): void {
    if (this.config.allowCustomPresets) {
      const storageKey = this.config.storageKey ?? 'filter-presets';
      localStorage.setItem(storageKey, JSON.stringify(this.customPresets));
    }
  }

  private generateId(): string {
    return 'preset-' + Date.now() + '-' + Math.random().toString(36).substring(2, 11);
  }
}
