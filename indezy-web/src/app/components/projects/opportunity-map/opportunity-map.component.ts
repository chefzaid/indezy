import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, ViewEncapsulation, inject
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import * as L from 'leaflet';

import { OpportunityMap, OpportunityPin, PROJECT_STATUS_COLORS, ProjectStatus } from '../../../models';
import { AuthService } from '../../../services/auth/auth.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/**
 * OpenStreetMap's standard tiles, used under its tile usage policy: no key, its attribution shown. ngsw-bypass keeps
 * the service worker out of their way: it would fetch them under its connect-src 'self', while the page loads them as
 * images, which its policy allows from any HTTPS host.
 */
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png?ngsw-bypass=true';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
/** Leaflet's stylesheet, built apart (angular.json) so only this page loads it. */
const LEAFLET_STYLES = 'leaflet.css';
/** France, shown until the opportunities are placed. */
const FRANCE: L.LatLngTuple = [46.6, 2.4];

/**
 * The opportunities on OpenStreetMap tiles (see docs/features.md#opportunity-map): a pin per opportunity at its place
 * of work or client's address, coloured by stage, around the home address; a pin's popup links to its opportunity.
 */
@Component({
  selector: 'app-opportunity-map',
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslateModule, PageHeaderComponent],
  templateUrl: './opportunity-map.component.html',
  styleUrls: ['./opportunity-map.component.scss'],
  // Leaflet builds the map's elements itself, outside Angular's style scoping.
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class OpportunityMapComponent implements OnInit, OnDestroy {
  private readonly commute = inject(CommuteService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly document = inject(DOCUMENT);

  @ViewChild('mapElement', { static: true }) mapElement!: ElementRef<HTMLElement>;

  readonly stages = Object.values(ProjectStatus);
  readonly colors = PROJECT_STATUS_COLORS;
  data: OpportunityMap | null = null;
  private map?: L.Map;

  ngOnInit(): void {
    this.loadStyles();
    this.map = L.map(this.mapElement.nativeElement, { center: FRANCE, zoom: 5, scrollWheelZoom: false });
    L.tileLayer(TILES, { attribution: ATTRIBUTION, maxZoom: 18 }).addTo(this.map);
    const freelanceId = this.auth.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    this.commute.getMap(freelanceId).subscribe({
      next: data => this.show(data),
      error: () => this.notifications.error('projects.map.loadError')
    });
  }

  /** Adds Leaflet's stylesheet to the page once, the first time a map opens. */
  private loadStyles(): void {
    if (this.document.querySelector('link[data-leaflet-styles]')) {
      return;
    }
    const link = this.document.createElement('link');
    link.rel = 'stylesheet';
    link.href = LEAFLET_STYLES;
    link.setAttribute('data-leaflet-styles', '');
    this.document.head.appendChild(link);
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  private show(data: OpportunityMap): void {
    this.data = data;
    this.changes.markForCheck();
    const map = this.map!;
    const points: L.LatLngTuple[] = [];
    if (data.home) {
      const home: L.LatLngTuple = [data.home.latitude, data.home.longitude];
      points.push(home);
      L.circleMarker(home, { radius: 9, color: '#1b1b24', weight: 3, fillColor: '#fbbf24', fillOpacity: 1 })
        .bindTooltip(this.translate.instant('projects.map.home')).addTo(map);
    }
    for (const pin of data.pins) {
      const point: L.LatLngTuple = [pin.latitude, pin.longitude];
      points.push(point);
      L.circleMarker(point, { radius: 8, color: '#ffffff', weight: 2, fillColor: PROJECT_STATUS_COLORS[pin.status], fillOpacity: 0.9,
        className: `pin pin-${pin.status}` }).bindPopup(this.popup(pin)).addTo(map);
    }
    if (points.length > 1) {
      map.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 12 });
    } else if (points.length === 1) {
      map.setView(points[0], 11);
    }
  }

  /** The popup's content built as elements, so a role or client name is shown as text, never read as markup. */
  private popup(pin: OpportunityPin): HTMLElement {
    const box = document.createElement('div');
    box.className = 'pin-popup';
    const link = document.createElement('a');
    link.href = `/projects/${pin.projectId}`;
    link.textContent = pin.role;
    link.addEventListener('click', event => {
      event.preventDefault();
      this.router.navigate(['/projects', pin.projectId]);
    });
    const facts = document.createElement('span');
    facts.textContent = [pin.clientName, this.translate.instant(`projects.statuses.${pin.status}`), pin.place].filter(Boolean).join(' · ');
    box.append(link, facts);
    return box;
  }
}
