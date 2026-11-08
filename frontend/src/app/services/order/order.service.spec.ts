import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { OrderService } from './order.service';
import { Order } from '../../model/order';

describe('OrderService', () => {
  let service: OrderService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should POST the order to the gateway and return the text answer', () => {
    let answer: string | undefined;
    const order: Order = {
      skuCode: 'iphone_15', price: 999, quantity: 1,
      userDetails: {email: 'testuser@example.com', firstName: 'Test', lastName: 'User'},
    };
    service.orderProduct(order).subscribe(a => answer = a);

    const req = httpTesting.expectOne('http://localhost:9000/api/order');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.skuCode).toBe('iphone_15');
    req.flush('Order Placed Successfully');
    expect(answer).toBe('Order Placed Successfully');
  });
});
