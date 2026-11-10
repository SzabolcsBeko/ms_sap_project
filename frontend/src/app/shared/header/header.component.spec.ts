import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HeaderComponent } from './header.component';
import { provideOidcSecurityServiceStub } from '../../../testing/oidc-security-service.stub';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [provideOidcSecurityServiceStub()],
    })
    .compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should greet the logged-in user', () => {
    expect(component.isAuthenticated).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Hi testuser');
  });
});
