import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';

import { routes } from './app.routes';
import { authConfig } from './config/auth.config';

describe('App routes and auth config', () => {
  it('should protect /add-product with the login guard (UI-8)', () => {
    const addProduct = routes.find(r => r.path === 'add-product');
    expect(addProduct?.canActivate).toContain(autoLoginPartialRoutesGuard);
  });

  it('should keep the home page public', () => {
    expect(routes.find(r => r.path === '')?.canActivate).toBeUndefined();
  });

  it('should accept refreshed Keycloak ID tokens (UI-3)', () => {
    const config = (Array.isArray(authConfig.config) ? authConfig.config[0] : authConfig.config)!;
    expect(config.ignoreNonceAfterRefresh).toBeTrue();
    expect(config.silentRenew).toBeTrue();
    expect(config.useRefreshToken).toBeTrue();
  });
});
