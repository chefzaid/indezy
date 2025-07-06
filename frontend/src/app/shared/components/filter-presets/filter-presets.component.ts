import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface FilterPreset {
  id: string;
  name: string;
  description?: string;
  filters: Record<string, any>;
  isDefault?: boolean;
  createdAt?: Date;
}

export interface FilterPresetsConfig {
  allowCustomPresets?: boolean;
  maxPresets?: number;
  storageKey?: string;
}

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
  template: `
    <div class="filter-presets">
      <div class="preset-actions">
        <button mat-button 
                [matMenuTriggerFor]="presetsMenu"
                class="presets-button">
          <mat-icon>bookmark</mat-icon>
          Filtres prédéfinis
          <mat-icon>arrow_drop_down</mat-icon>
        </button>

        <button mat-icon-button 
                *ngIf="config.allowCustomPresets && hasActiveFilters"
                (click)="saveCurrentAsPreset()"
                matTooltip="Sauvegarder les filtres actuels"
                class="save-preset-button">
          <mat-icon>bookmark_add</mat-icon>
        </button>
      </div>

      <mat-menu #presetsMenu="matMenu" class="presets-menu">
        <div class="preset-header">
          <span class="preset-title">Filtres prédéfinis</span>
        </div>

        <div class="preset-list">
          <button mat-menu-item 
                  *ngFor="let preset of presets"
                  (click)="applyPreset(preset)"
                  class="preset-item">
            <mat-icon>{{ preset.isDefault ? 'star' : 'bookmark' }}</mat-icon>
            <div class="preset-info">
              <span class="preset-name">{{ preset.name }}</span>
              <span class="preset-description" *ngIf="preset.description">
                {{ preset.description }}
              </span>
            </div>
            <button mat-icon-button 
                    *ngIf="!preset.isDefault && config.allowCustomPresets"
                    (click)="deletePreset(preset, $event)"
                    class="delete-preset">
              <mat-icon>delete</mat-icon>
            </button>
          </button>
        </div>

        <div class="preset-footer" *ngIf="config.allowCustomPresets">
          <button mat-menu-item 
                  (click)="clearAllPresets()"
                  *ngIf="customPresets.length > 0"
                  class="clear-presets">
            <mat-icon>clear_all</mat-icon>
            Effacer tous les filtres personnalisés
          </button>
        </div>
      </mat-menu>
    </div>
  `,
  styles: [`
    .filter-presets {
      .preset-actions {
        display: flex;
        align-items: center;
        gap: 8px;

        .presets-button {
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .save-preset-button {
          color: #1976d2;
        }
      }
    }

    .presets-menu {
      min-width: 300px;

      .preset-header {
        padding: 12px 16px;
        border-bottom: 1px solid #e0e0e0;

        .preset-title {
          font-weight: 500;
          color: #333;
        }
      }

      .preset-list {
        max-height: 300px;
        overflow-y: auto;

        .preset-item {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 12px 16px;
          width: 100%;
          text-align: left;

          mat-icon {
            color: #666;
          }

          .preset-info {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 2px;

            .preset-name {
              font-weight: 500;
              color: #333;
            }

            .preset-description {
              font-size: 12px;
              color: #666;
            }
          }

          .delete-preset {
            opacity: 0;
            transition: opacity 0.2s;

            mat-icon {
              color: #f44336;
              font-size: 18px;
              width: 18px;
              height: 18px;
            }
          }

          &:hover .delete-preset {
            opacity: 1;
          }
        }
      }

      .preset-footer {
        border-top: 1px solid #e0e0e0;
        padding-top: 8px;

        .clear-presets {
          color: #f44336;
          width: 100%;
        }
      }
    }
  `]
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
        name: 'Prospects',
        description: 'Clients avec statut prospect',
        filters: { status: 'PROSPECT' },
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
