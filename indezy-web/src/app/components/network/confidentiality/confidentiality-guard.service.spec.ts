import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { NetworkService } from '../../../services/network/network.service';
import { ConfidentialityChoice } from './confidentiality-dialog.component';
import { ConfidentialityGuardService } from './confidentiality-guard.service';

describe('ConfidentialityGuardService', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let guard: ConfidentialityGuardService;

  beforeEach(() => {
    network = jasmine.createSpyObj('NetworkService', ['checkConfidentiality']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({ providers: [{ provide: NetworkService, useValue: network }, { provide: MatDialog, useValue: dialog }] });
    guard = TestBed.inject(ConfidentialityGuardService);
  });

  function review(text: string): ConfidentialityChoice | null | undefined {
    let choice: ConfidentialityChoice | null | undefined;
    guard.review(text).subscribe(result => choice = result);
    return choice;
  }

  it('publishes what names nobody, or when the check is unavailable', () => {
    network.checkConfidentiality.and.returnValue(of([]));
    expect(review('Un retour sur Kafka')).toEqual({ action: 'publish', text: 'Un retour sur Kafka' });
    network.checkConfidentiality.and.returnValue(throwError(() => new Error('down')));
    expect(review('Encore')).toEqual({ action: 'publish', text: 'Encore' });
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('asks the member when it names the workspace\'s clients, cancelling as none', () => {
    const matches = [{ found: 'Acme', kind: 'CLIENT' as const, city: null }];
    network.checkConfidentiality.and.returnValue(of(matches));
    dialog.open.and.returnValue({ afterClosed: () => of({ action: 'edit', text: 'Chez un client' }) } as never);
    expect(review('Chez Acme')).toEqual({ action: 'edit', text: 'Chez un client' });
    expect(dialog.open).toHaveBeenCalledWith(jasmine.any(Function), { data: { text: 'Chez Acme', matches } });
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);
    expect(review('Chez Acme')).toBeNull();
  });
});
