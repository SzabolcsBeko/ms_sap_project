import {Provider} from '@angular/core';
import {OidcSecurityService} from 'angular-auth-oidc-client';
import {of} from 'rxjs';

/**
 * Test double for OidcSecurityService, so unit tests don't need a real Keycloak.
 * Covers only the members the app uses (see app.component, header, home-page,
 * auth.interceptor). It represents a logged-in user by default.
 *
 * Note: the logged-out case (userData null) isn't the default, because the header
 * currently crashes on it ("reading 'preferred_username'"), a known separate bug.
 */
export function createOidcSecurityServiceStub(isAuthenticated = true) {
  const userData = isAuthenticated
    ? {preferred_username: 'testuser', email: 'testuser@example.com', given_name: 'Test', family_name: 'User'}
    : null;
  return {
    checkAuth: jasmine.createSpy('checkAuth').and.returnValue(of({isAuthenticated, userData, accessToken: '', idToken: ''})),
    isAuthenticated$: of({isAuthenticated, allConfigsAuthenticated: []}),
    userData$: of({userData, allUserData: []}),
    getAccessToken: jasmine.createSpy('getAccessToken').and.returnValue(of(isAuthenticated ? 'test-token' : '')),
    authorize: jasmine.createSpy('authorize'),
    logoff: jasmine.createSpy('logoff').and.returnValue(of(null)),
  };
}

export function provideOidcSecurityServiceStub(isAuthenticated = true): Provider {
  return {provide: OidcSecurityService, useValue: createOidcSecurityServiceStub(isAuthenticated)};
}
