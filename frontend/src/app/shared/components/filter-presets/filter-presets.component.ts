import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FilterPreset, FilterPresetsConfig } from '../../../models/filter.models';

@Component({
  selector: 'app-filter-presets',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatMenuModule,
    MatIconModule,
    MatDialogModule
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
    private readonly snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadPresets();
  }

  applyPreset(preset: FilterPreset): void {
    this.presetApplied.emit(preset);
    this.snackBar.open(`Filtre "${preset.name}" appliqué`, 'Fermer', {
      duration: 2000
    });
  }

  saveCurrentAsPreset(): void {
    const name = prompt('Nom du filtre prédéfini:');
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
      this.snackBar.open(`Filtre "${preset.name}" sauvegardé`, 'Fermer', {
        duration: 2000
      });
    }
  }

  deletePreset(preset: FilterPreset, event: Event): void {
    event.stopPropagation();
    
    if (confirm(`Supprimer le filtre "${preset.name}" ?`)) {
      const index = this.customPresets.findIndex(p => p.id === preset.id);
      if (index >= 0) {
        this.customPresets.splice(index, 1);
        this.savePresets();
        this.loadPresets();
        
        this.presetDeleted.emit(preset);
        this.snackBar.open(`Filtre "${preset.name}" supprimé`, 'Fermer', {
          duration: 2000
        });
      }
    }
  }

  clearAllPresets(): void {
    if (confirm('Supprimer tous les filtres personnalisés ?')) {
      this.customPresets = [];
      this.savePresets();
      this.loadPresets();
      
      this.snackBar.open('Tous les filtres personnalisés ont été supprimés', 'Fermer', {
        duration: 2000
      });
    }
  }

  private loadPresets(): void {
    // Load default presets
    const defaultPresets: FilterPreset[] = [
      {
        id: 'active-clients',
        name: 'Clients actifs',
        description: 'Clients avec statut actif',
        filters: { status: 'ACTIVE' },
        isDefault: true
      },
      {
        id: 'recent-clients',
        name: 'Clients récents',
        description: 'Clients créés dans les 30 derniers jours',
        filters: { 
          createdDateFrom: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
        },
        isDefault: true
      },
      {
        id: 'prospects',
        name: 'ESN',
        description: 'Clients avec statut ESN',
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
