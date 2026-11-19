import { Component, Input, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { NetworkNavComponent } from './network-nav.component';

/** The network's sections in a page's tests: the bar is tested on its own (network-nav.component.spec.ts). */
@Component({ selector: 'app-network-nav', template: '<nav data-network-nav></nav>' })
export class NetworkNavStubComponent {
  @Input() back = false;
}

/** Puts the stub in place of the bar in the page under test; called once its testing module is configured. */
export function stubNetworkNav(page: Type<unknown>): void {
  TestBed.overrideComponent(page, { remove: { imports: [NetworkNavComponent] }, add: { imports: [NetworkNavStubComponent] } });
}
