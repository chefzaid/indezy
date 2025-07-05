import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule
  ],
  template: `
    <div class="profile-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>
            <mat-icon>person</mat-icon>
            Profil utilisateur
          </mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p>Cette fonctionnalité sera bientôt disponible.</p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .profile-container {
      padding: 24px;
      max-width: 800px;
      margin: 0 auto;
    }
    
    mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  `]
})
export class ProfileComponent {}
