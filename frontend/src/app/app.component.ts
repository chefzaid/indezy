import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from './services/auth/auth.service';

interface MenuItem {
  label: string;
  route: string;
  icon: string;
}

@Component({
    selector: 'app-root',
    imports: [
        CommonModule,
        RouterOutlet,
        RouterModule,
        MatToolbarModule,
        MatButtonModule,
        MatIconModule,
        MatSidenavModule,
        MatListModule,
        MatMenuModule
    ],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  title = 'Indezy - Suivi de missions pour freelances';
  currentUser: { id: number; email: string; firstName: string; lastName: string; role?: string } | null = null;

  menuItems: MenuItem[] = [
    { label: 'Tableau de bord', route: '/dashboard', icon: 'dashboard' },
    { label: 'Projets', route: '/projects', icon: 'work' },
    { label: 'Clients', route: '/clients', icon: 'business' },
    { label: 'Sources', route: '/sources', icon: 'source' }
  ];

  constructor(
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
