import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NETWORK_SEARCH_DELAY_MS, NetworkSearchComponent, NetworkSearchOption } from './network-search.component';

describe('NetworkSearchComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  const member = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [],
    city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });

  function create() {
    network = jasmine.createSpyObj('NetworkService', ['searchMembers', 'suggestTags']);
    network.searchMembers.and.returnValue(of({ members: ['a', 'b', 'c', 'd', 'e', 'f'].map(letter => member('leo_' + letter)), total: 6,
      page: 0, size: 20, followed: [], requested: [], privateCards: [] }));
    network.suggestTags.and.returnValue(of(['kafka', 'kafka-streams']));
    TestBed.configureTestingModule({
      imports: [NetworkSearchComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }]
    });
    const fixture = TestBed.createComponent(NetworkSearchComponent);
    fixture.detectChanges();
    return fixture;
  }

  const picked = (value: NetworkSearchOption) => ({ option: { value } }) as unknown as MatAutocompleteSelectedEvent;

  it('suggests members and tags once two letters are typed, five of each at most, then all the results', fakeAsync(() => {
    const component = create().componentInstance;
    component.query.setValue('k');
    tick(NETWORK_SEARCH_DELAY_MS);
    expect(network.searchMembers).not.toHaveBeenCalled();
    expect(component.options).toEqual([]);
    component.query.setValue('ka');
    tick(NETWORK_SEARCH_DELAY_MS);
    expect(network.searchMembers).toHaveBeenCalledWith('ka');
    expect(network.suggestTags).toHaveBeenCalledWith('ka');
    expect(component.options.filter(option => option.kind === 'member').length).toBe(5);
    expect(component.options.filter(option => option.kind === 'tag').length).toBe(2);
    expect(component.options.at(-1)).toEqual({ kind: 'all', query: 'ka' });
    // Words are no tag: only members are asked for.
    network.suggestTags.calls.reset();
    component.query.setValue('bob mar');
    tick(NETWORK_SEARCH_DELAY_MS);
    expect(network.suggestTags).not.toHaveBeenCalled();
    // @ looks for members only, # for tags only; a search that fails suggests nothing of its kind.
    network.searchMembers.calls.reset();
    network.suggestTags.calls.reset();
    component.query.setValue('#kaf');
    tick(NETWORK_SEARCH_DELAY_MS);
    expect(network.searchMembers).not.toHaveBeenCalled();
    expect(network.suggestTags).toHaveBeenCalledWith('kaf');
    network.suggestTags.calls.reset();
    network.searchMembers.and.returnValue(throwError(() => new Error('down')));
    component.query.setValue('@leo');
    tick(NETWORK_SEARCH_DELAY_MS);
    expect(network.suggestTags).not.toHaveBeenCalled();
    expect(component.options).toEqual([{ kind: 'all', query: '@leo' }]);
  }));

  it('opens the page of what is picked, and the results for what was typed on Enter or See all', () => {
    const component = create().componentInstance;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    component.pick(picked({ kind: 'member', member: member('leo_m') }));
    expect(navigate).toHaveBeenCalledWith(['/m', 'leo_m']);
    component.pick(picked({ kind: 'tag', tag: 'kafka' }));
    expect(navigate).toHaveBeenCalledWith(['/network/tags', 'kafka']);
    component.query.setValue('  #Spark ', { emitEvent: false });
    expect(component.display()).toBe('  #Spark ');
    const submit = new Event('submit', { cancelable: true });
    component.search(submit);
    expect(submit.defaultPrevented).toBeTrue();
    expect(navigate).toHaveBeenCalledWith(['/network/search'], { queryParams: { q: '#Spark' } });
    expect(component.query.value).toBe('');
    component.pick(picked({ kind: 'all', query: 'kafka lyon' }));
    expect(navigate).toHaveBeenCalledWith(['/network/search'], { queryParams: { q: 'kafka lyon' } });
    navigate.calls.reset();
    component.search();
    component.query.setValue('#k', { emitEvent: false });
    component.search();
    expect(navigate).not.toHaveBeenCalled();
  });
});
