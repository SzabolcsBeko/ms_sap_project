import {Component, inject} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";
import {Product} from "../../model/product";
import {ProductService} from "../../services/product/product.service";
import {NgIf} from "@angular/common";
import {RouterLink} from "@angular/router";

@Component({
  selector: 'app-add-product',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf, RouterLink],
  templateUrl: './add-product.component.html',
  styleUrl: './add-product.component.css'
})
export class AddProductComponent {
  addProductForm: FormGroup;
  private readonly productService = inject(ProductService);
  productCreated = false;
  productCreateFailed = false;

  constructor(private fb: FormBuilder) {
    this.addProductForm = this.fb.group({
      // Same rules the template's messages describe
      skuCode: ['', [Validators.required, Validators.minLength(3)]],
      name: ['', [Validators.required, Validators.minLength(3)]],
      description: ['', [Validators.required, Validators.minLength(10)]],
      price: [0, [Validators.required, Validators.min(0.01)]]
    })
  }

  onSubmit(): void {
    this.productCreated = false;
    this.productCreateFailed = false;
    if (this.addProductForm.valid) {
      const product: Product = {
        skuCode: this.addProductForm.get('skuCode')?.value,
        name: this.addProductForm.get('name')?.value,
        description: this.addProductForm.get('description')?.value,
        price: this.addProductForm.get('price')?.value
      }
      this.productService.createProduct(product).subscribe({
        next: () => {
          this.productCreated = true;
          this.addProductForm.reset({skuCode: '', name: '', description: '', price: 0});
        },
        error: () => this.productCreateFailed = true
      })
    } else {
      console.log('Form is not valid');
    }
  }

  get skuCode() {
    return this.addProductForm.get('skuCode');
  }

  get name() {
    return this.addProductForm.get('name');
  }

  get description() {
    return this.addProductForm.get('description');
  }

  get price() {
    return this.addProductForm.get('price');
  }
}
