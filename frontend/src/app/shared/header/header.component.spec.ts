import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';

import { HeaderComponent } from './header.component';
import { provideOidcSecurityServiceStub } from '../../../testing/oidc-security-service.stub';

function setup(isAuthenticated: boolean) {
  TestBed.configureTestingModule({
    imports: [HeaderComponent],
    providers: [provideRouter([]), provideOidcSecurityServiceStub(isAuthenticated)],
  });
  const fixture = TestBed.createComponent(HeaderComponent);
  fixture.detectChanges();
  return fixture;
}

const text = (fixture: ComponentFixture<HeaderComponent>) =>
  (fixture.nativeElement as HTMLElement).textContent ?? '';

describe('HeaderComponent', () => {
  describe('logged in', () => {
    let fixture: ComponentFixture<HeaderComponent>;
    beforeEach(() => fixture = setup(true));

    it('should create', () => {
      expect(fixture.componentInstance).toBeTruthy();
    });

    it('should greet the logged-in user', () => {
      expect(fixture.componentInstance.isAuthenticated).toBeTrue();
      expect(text(fixture)).toContain('Hi testuser');
    });

    it('should show the greeting in a colour that is readable in light mode (UI-7)', () => {
      const greeting = (fixture.nativeElement as HTMLElement).querySelector('p')!;
      expect(greeting.classList).toContain('text-gray-900');
      expect(greeting.classList).toContain('dark:text-white');
      expect(greeting.classList).not.toContain('text-white');
    });

    it('should log out', () => {
      const logout = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
        .find(b => b.textContent?.trim() === 'Logout')!;
      logout.click();
      expect(TestBed.inject(OidcSecurityService).logoff).toHaveBeenCalled();
    });
  });

  describe('logged out', () => {
    let fixture: ComponentFixture<HeaderComponent>;
    beforeEach(() => fixture = setup(false));

    it('should not crash when there is no user data', () => {
      expect(fixture.componentInstance.username).toBe('');
      expect(text(fixture)).toContain('Login');
      expect(text(fixture)).not.toContain('Hi ');
    });

    it('should start the login', () => {
      const login = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
        .find(b => b.textContent?.trim() === 'Login')!;
      login.click();
      expect(TestBed.inject(OidcSecurityService).authorize).toHaveBeenCalled();
    });
  });

  it('should open and close the mobile menu with the menu button (UI-1)', () => {
    const fixture = setup(true);
    const el = fixture.nativeElement as HTMLElement;
    const toggle = el.querySelector<HTMLButtonElement>('#menu-toggle')!;
    const menu = el.querySelector('#mobile-menu')!;
    expect(menu.classList).toContain('hidden');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(menu.classList).not.toContain('hidden');
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    fixture.detectChanges();
    expect(menu.classList).toContain('hidden');
  });
});
