import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from './services/auth/auth.service';

interface MenuItem {
  labelKey: string;
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
        MatMenuModule,
        TranslateModule
    ],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  title = 'Indezy';
  currentUser: { id: number; email: string; firstName: string; lastName: string; role?: string } | null = null;
  currentLang = 'fr';

  menuItems: MenuItem[] = [
    { labelKey: 'nav.dashboard', route: '/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.projects', route: '/projects', icon: 'work' },
    { labelKey: 'nav.clients', route: '/clients', icon: 'business' },
    { labelKey: 'nav.contacts', route: '/contacts', icon: 'contacts' },
    { labelKey: 'nav.sources', route: '/sources', icon: 'source' }
  ];

  constructor(
    public authService: AuthService,
    private readonly translate: TranslateService
  ) {
    const savedLang = localStorage.getItem('indezy-lang') || 'fr';
    this.currentLang = savedLang;
    this.translate.use(savedLang);
  }

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  switchLanguage(lang: string): void {
    this.currentLang = lang;
    this.translate.use(lang);
    localStorage.setItem('indezy-lang', lang);
  }

  logout(): void {
    this.authService.logout();
  }
}
