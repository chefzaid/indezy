import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { ContactDto, ContactRelation } from '../../../models/contact.models';
import { ContactService } from '../../../services/contact/contact.service';

/** A contact of the client with the people reporting to it there, and those it works with elsewhere. */
export interface OrgNode {
  contact: ContactDto;
  reports: OrgNode[];
  worksWith: ContactRelation[];
  /** Whom it reports to at another company, when it does. */
  externalManager?: string;
}

/**
 * The client's contacts as an org chart: who reports to whom there (contacts reporting to nobody at the client at the
 * top), and, beside each, the people it works with at other companies, such as an ESN's account manager.
 */
export function buildOrgChart(contacts: ContactDto[], relations: ContactRelation[]): OrgNode[] {
  const ids = new Set(contacts.map(contact => contact.id));
  const name = (contact: ContactDto): string => [contact.firstName, contact.lastName].filter(Boolean).join(' ');
  const byName = (a: OrgNode, b: OrgNode): number => name(a.contact).localeCompare(name(b.contact));
  const nodes = new Map(contacts.map(contact => [contact.id, {
    contact,
    reports: [] as OrgNode[],
    worksWith: relations.filter(relation => relation.contactId === contact.id),
    externalManager: contact.managerId && !ids.has(contact.managerId) ? contact.managerName : undefined
  } as OrgNode]));
  const roots: OrgNode[] = [];
  for (const node of nodes.values()) {
    const manager = node.contact.managerId ? nodes.get(node.contact.managerId) : undefined;
    (manager ? manager.reports : roots).push(node);
  }
  for (const node of nodes.values()) {
    node.reports.sort(byName);
  }
  // A loop stored before the server refused them would leave no root: show everyone flat rather than nothing.
  return (roots.length ? roots : [...nodes.values()].map(node => ({ ...node, reports: [] }))).sort(byName);
}

@Component({
  selector: 'app-client-org-chart',
  imports: [NgTemplateOutlet, RouterModule, MatIconModule, TranslateModule],
  templateUrl: './client-org-chart.component.html',
  styleUrls: ['./client-org-chart.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ClientOrgChartComponent implements OnChanges {
  @Input({ required: true }) clientId!: number;
  @Input({ required: true }) contacts: ContactDto[] = [];

  private readonly contactService = inject(ContactService);
  private relations: ContactRelation[] = [];
  roots: OrgNode[] = [];

  ngOnChanges(): void {
    this.roots = buildOrgChart(this.contacts, this.relations);
    this.contactService.getClientRelations(this.clientId).subscribe({
      next: relations => {
        this.relations = relations;
        this.roots = buildOrgChart(this.contacts, relations);
      },
      // The chart still shows who reports to whom.
      error: () => this.relations = []
    });
  }

  nameOf(contact: ContactDto): string {
    return [contact.firstName, contact.lastName].filter(Boolean).join(' ');
  }
}
