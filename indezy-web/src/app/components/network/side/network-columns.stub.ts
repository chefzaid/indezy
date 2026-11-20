import { Component, EventEmitter, Input, Output, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkSideComponent } from './network-side.component';
import { CardStrengthComponent } from '../card-strength/card-strength.component';

/** The reader's page in a page's tests: the card is tested on its own (network-me-card.component.spec.ts). */
@Component({ selector: 'app-network-me-card', template: '<section data-me-card-stub></section>' })
export class NetworkMeCardStubComponent {}

/** The right column in a page's tests: tested on its own (network-side.component.spec.ts). */
@Component({ selector: 'app-network-side', template: '<section data-side-stub></section>' })
export class NetworkSideStubComponent {
  @Input() exclude: string | null = null;
  @Output() readonly followed = new EventEmitter<string>();
}

/** The card's strength in a page's tests: tested on its own (card-strength.component.spec.ts). */
@Component({ selector: 'app-card-strength', template: '<section data-card-strength-stub></section>' })
export class CardStrengthStubComponent {}

/** Puts the stubs in place of the side columns in the page under test; called once its testing module is configured. */
export function stubNetworkColumns(page: Type<unknown>): void {
  TestBed.overrideComponent(page, {
    remove: { imports: [NetworkMeCardComponent, NetworkSideComponent, CardStrengthComponent] },
    add: { imports: [NetworkMeCardStubComponent, NetworkSideStubComponent, CardStrengthStubComponent] }
  });
}
