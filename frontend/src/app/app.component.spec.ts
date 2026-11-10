import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { AppComponent } from './app.component';
import { provideOidcSecurityServiceStub } from '../testing/oidc-security-service.stub';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), provideOidcSecurityServiceStub()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'microservices-shop-frontend' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('microservices-shop-frontend');
  });

  // The app template is <app-header> + <router-outlet>; the CLI default test looked for an <h1> that never existed.
  it('should render the header and the router outlet', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).not.toBeNull();
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
    expect(compiled.textContent).toContain('Spring Boot Microservices Shop');
  });

  it('should check the login state on start', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(TestBed.inject(OidcSecurityService).checkAuth).toHaveBeenCalled();
  });
});
