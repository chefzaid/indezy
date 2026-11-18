import { environment } from '../../../environments/environment';

/** Sign-up opens to the public before launch (TODO.md, Needs Input): until then it is offered in development only. */
export const SIGN_UP_OPEN = !environment.production;
