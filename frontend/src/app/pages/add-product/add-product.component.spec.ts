import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AddProductComponent } from './add-product.component';

const PRODUCT_URL = 'http://localhost:9000/api/product';

describe('AddProductComponent', () => {
  let component: AddProductComponent;
  let fixture: ComponentFixture<AddProductComponent>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddProductComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    })
    .compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddProductComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const submitButton = () => el().querySelector<HTMLButtonElement>('button[type=submit]')!;
  function fill(values: {skuCode: string; name: string; description: string; price: number}) {
    component.addProductForm.setValue(values);
    component.addProductForm.markAllAsTouched();
    fixture.detectChanges();
  }
  const VALID = {skuCode: 'oneplus_12', name: 'OnePlus 12', description: 'OnePlus flagship phone', price: 799};

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have a link back to the product list (UI-9)', () => {
    const back = el().querySelector('a[href="/"]');
    expect(back?.textContent).toContain('Back to products');
  });

  it('should reject too-short values and a price of 0 (UI-5)', () => {
    fill({skuCode: 'ab', name: 'x', description: 'short', price: 0});
    expect(component.addProductForm.invalid).toBeTrue();
    expect(submitButton().disabled).toBeTrue();
    const text = el().textContent;
    expect(text).toContain('SKU Code must be at least 3 characters long.');
    expect(text).toContain('Name must be at least 3 characters long.');
    expect(text).toContain('Description must be at least 10 characters long.');
    expect(text).toContain('Price must be greater than 0.');
  });

  it('should accept valid values', () => {
    fill(VALID);
    expect(component.addProductForm.valid).toBeTrue();
    expect(submitButton().disabled).toBeFalse();
  });

  it('should create the product, show success and clear the form', () => {
    fill(VALID);
    submitButton().click();
    const req = httpTesting.expectOne(PRODUCT_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(VALID);
    req.flush({id: '1', ...VALID});
    fixture.detectChanges();
    expect(el().textContent).toContain('Product Created Successfully');
    expect(component.addProductForm.value).toEqual({skuCode: '', name: '', description: '', price: 0});
  });

  it('should show an error when saving fails and keep the values (UI-8)', () => {
    fill(VALID);
    submitButton().click();
    httpTesting.expectOne(PRODUCT_URL).flush('', {status: 401, statusText: 'Unauthorized'});
    fixture.detectChanges();
    expect(el().textContent).toContain('Product could not be created');
    expect(el().textContent).not.toContain('Product Created Successfully');
    expect(component.addProductForm.value).toEqual(VALID);
  });
});
