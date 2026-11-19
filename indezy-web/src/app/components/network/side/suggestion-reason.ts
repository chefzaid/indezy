import { TranslateService } from '@ngx-translate/core';

import { MemberSuggestion } from '../../../models';

/** Why a member is suggested, in words: a contact, the same ESN, followed by members one follows, the same skills or city. */
export function suggestionReason(translate: TranslateService, suggestion: MemberSuggestion): string {
  switch (suggestion.reason) {
    case 'CONTACT':
      return translate.instant('network.suggestions.contact');
    case 'SAME_ESN':
      return translate.instant('network.suggestions.sameEsn', { esn: suggestion.esn });
    case 'FOLLOWED_BY':
      return translate.instant(suggestion.followedBy > 1 ? 'network.suggestions.followedByMany' : 'network.suggestions.followedByOne',
        { name: suggestion.followedByName, others: suggestion.followedBy - 1 });
    case 'SAME_STACK':
      return translate.instant('network.suggestions.sameStack', { skills: suggestion.sharedSkills.join(', ') });
    default:
      return translate.instant('network.suggestions.sameCity', { city: suggestion.card.city });
  }
}
