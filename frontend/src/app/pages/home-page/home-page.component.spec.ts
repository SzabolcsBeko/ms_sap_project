import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { HomePageComponent } from './home-page.component';
import { provideOidcSecurityServiceStub } from '../../../testing/oidc-security-service.stub';

describe('HomePageComponent', () => {
  let component: HomePageComponent;
  let fixture: ComponentFixture<HomePageComponent>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideOidcSecurityServiceStub()],
    })
    .compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HomePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    httpTesting.expectOne('http://localhost:9000/api/product').flush([]);
    expect(component).toBeTruthy();
  });

  it('should load the products', () => {
    httpTesting.expectOne('http://localhost:9000/api/product')
      .flush([{id: '1', skuCode: 'iphone_15', name: 'iPhone 15', description: 'Apple phone', price: 999}]);
    expect(component.products.length).toBe(1);
    expect(component.products[0].skuCode).toBe('iphone_15');
  });
});
