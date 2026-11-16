import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { BehaviorSubject } from 'rxjs';

import { HomePageComponent } from './home-page.component';
import { createOidcSecurityServiceStub } from '../../../testing/oidc-security-service.stub';
import { Product } from '../../model/product';

const PRODUCT_URL = 'http://localhost:9000/api/product';
const ORDER_URL = 'http://localhost:9000/api/order';
const USER = {preferred_username: 'testuser', email: 'testuser@example.com', given_name: 'Test', family_name: 'User'};
const PRODUCTS: Product[] = [
  {id: '1', skuCode: 'iphone_15', name: 'iPhone 15', description: 'Apple phone', price: 999},
  {id: '2', skuCode: 'pixel_8', name: 'Pixel 8', description: 'Google phone', price: 699},
];

describe('HomePageComponent', () => {
  let fixture: ComponentFixture<HomePageComponent>;
  let component: HomePageComponent;
  let httpTesting: HttpTestingController;
  // Subjects, so a test can log the user out after the page has loaded
  let isAuthenticated$: BehaviorSubject<{isAuthenticated: boolean; allConfigsAuthenticated: []}>;
  let userData$: BehaviorSubject<{userData: typeof USER | null; allUserData: []}>;

  function create(loggedIn: boolean) {
    isAuthenticated$ = new BehaviorSubject({isAuthenticated: loggedIn, allConfigsAuthenticated: [] as []});
    userData$ = new BehaviorSubject<{userData: typeof USER | null; allUserData: []}>({userData: loggedIn ? USER : null, allUserData: []});
    const oidc = {...createOidcSecurityServiceStub(loggedIn), isAuthenticated$, userData$};

    TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        {provide: OidcSecurityService, useValue: oidc}],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HomePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const rows = () => Array.from(el().querySelectorAll('li'));
  function order(row: number, quantity: string) {
    rows()[row].querySelector('input')!.value = quantity;
    rows()[row].querySelector('button')!.click();
    fixture.detectChanges();
  }
  function loadProducts() {
    httpTesting.expectOne(PRODUCT_URL).flush(PRODUCTS);
    fixture.detectChanges();
  }

  afterEach(() => httpTesting.verify());

  describe('logged out', () => {
    beforeEach(() => create(false));

    it('should not call the product API and ask the user to log in (UI-6)', () => {
      httpTesting.expectNone(PRODUCT_URL);
      expect(el().textContent).toContain('Please log in to see the products.');
      expect(el().textContent).not.toContain('No products found!');
    });
  });

  describe('logged in', () => {
    beforeEach(() => create(true));

    it('should load the products', () => {
      loadProducts();
      expect(component.products.length).toBe(2);
      expect(rows().length).toBe(2);
    });

    it('should show an error when the products cannot be loaded', () => {
      httpTesting.expectOne(PRODUCT_URL).flush('boom', {status: 500, statusText: 'Server Error'});
      fixture.detectChanges();
      expect(component.loadFailed).toBeTrue();
      expect(el().textContent).toContain('Could not load the products');
    });

    it('should clear the list when the user is logged out (UI-4)', () => {
      loadProducts();
      isAuthenticated$.next({isAuthenticated: false, allConfigsAuthenticated: []});
      userData$.next({userData: null, allUserData: []});
      fixture.detectChanges();
      expect(rows().length).toBe(0);
      expect(el().textContent).toContain('Please log in');
    });

    it('should not send an order without a quantity', () => {
      loadProducts();
      order(0, '');
      httpTesting.expectNone(ORDER_URL);
      expect(rows()[0].textContent).toContain('Quantity cannot be empty');
    });

    it('should send the order with the Keycloak user details and show the result in that row (UI-9)', () => {
      loadProducts();
      order(1, '2');
      const req = httpTesting.expectOne(ORDER_URL);
      expect(req.request.body).toEqual({
        skuCode: 'pixel_8', price: 699, quantity: 2,
        userDetails: {email: 'testuser@example.com', firstName: 'Test', lastName: 'User'},
      });
      req.flush('Order Placed Successfully');
      fixture.detectChanges();
      expect(rows()[1].textContent).toContain('Order Placed Successfully');
      expect(rows()[0].querySelector('[role=status]')).toBeNull();
    });

    it('should replace an earlier success with a later failure (UI-2)', () => {
      loadProducts();
      order(0, '1');
      httpTesting.expectOne(ORDER_URL).flush('Order Placed Successfully');
      order(0, '1000');
      httpTesting.expectOne(ORDER_URL).flush('out of stock', {status: 500, statusText: 'Server Error'});
      fixture.detectChanges();
      expect(rows()[0].textContent).toContain('Order Failed, please try again later');
      expect(rows()[0].textContent).not.toContain('Order Placed Successfully');
    });

    it('should send one request per click, also after the user data changes (UI-4)', () => {
      loadProducts();
      order(0, '1');
      httpTesting.expectOne(ORDER_URL).flush('ok');
      // a user-data refresh must not replay the earlier click
      userData$.next({userData: {...USER}, allUserData: []});
      httpTesting.expectNone(ORDER_URL);
    });

    it('should ask to log in again when the user data is gone', () => {
      loadProducts();
      userData$.next({userData: null, allUserData: []});
      order(0, '1');
      httpTesting.expectNone(ORDER_URL);
      expect(rows()[0].textContent).toContain('please log in again');
    });
  });
});
