import {Component, inject, OnInit} from '@angular/core';
import {OidcSecurityService} from "angular-auth-oidc-client";
import {Product} from "../../model/product";
import {ProductService} from "../../services/product/product.service";
import {Router} from "@angular/router";
import {Order} from "../../model/order";
import {FormsModule} from "@angular/forms";
import {OrderService} from "../../services/order/order.service";
import {take} from "rxjs";

/** Result of the last order, shown next to the product it was placed for. */
export interface OrderResult {
  productKey: string;
  success: boolean;
  message: string;
}

@Component({
  selector: 'app-homepage',
  templateUrl: './home-page.component.html',
  standalone: true,
  imports: [
    FormsModule
  ],
  styleUrl: './home-page.component.css'
})
export class HomePageComponent implements OnInit {
  private readonly oidcSecurityService = inject(OidcSecurityService);
  private readonly productService = inject(ProductService);
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);
  isAuthenticated = false;
  products: Array<Product> = [];
  loadFailed = false;
  orderResult: OrderResult | null = null;

  ngOnInit(): void {
    this.oidcSecurityService.isAuthenticated$.subscribe(
      ({isAuthenticated}) => {
        this.isAuthenticated = isAuthenticated;
        this.orderResult = null;
        if (isAuthenticated) {
          this.loadProducts();
        } else {
          // The API needs a token: don't call it, and don't keep a stale list after a logout
          this.products = [];
          this.loadFailed = false;
        }
      }
    )
  }

  private loadProducts(): void {
    this.productService.getProducts().subscribe({
      next: products => {
        this.products = products;
        this.loadFailed = false;
      },
      error: () => {
        this.products = [];
        this.loadFailed = true;
      }
    });
  }

  productKey(product: Product): string {
    return product.id ?? product.skuCode;
  }

  goToCreateProductPage() {
    this.router.navigateByUrl('/add-product');
  }

  orderProduct(product: Product, quantity: string) {
    const productKey = this.productKey(product);
    this.orderResult = null;

    if (!quantity) {
      this.orderResult = {productKey, success: false, message: 'Quantity cannot be empty'};
      return;
    }

    // take(1): read the current user once instead of keeping a subscription per click
    this.oidcSecurityService.userData$.pipe(take(1)).subscribe(({userData}) => {
      if (!userData) {
        this.orderResult = {productKey, success: false, message: 'Your session has ended, please log in again'};
        return;
      }

      const order: Order = {
        skuCode: product.skuCode,
        price: product.price,
        quantity: Number(quantity),
        userDetails: {
          email: userData.email,
          firstName: userData.given_name,
          lastName: userData.family_name
        }
      }

      this.orderService.orderProduct(order).subscribe({
        next: () => this.orderResult = {productKey, success: true, message: 'Order Placed Successfully'},
        error: () => this.orderResult = {productKey, success: false, message: 'Order Failed, please try again later'}
      });
    })
  }
}
