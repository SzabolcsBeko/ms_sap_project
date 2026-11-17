import {Routes} from '@angular/router';
import {autoLoginPartialRoutesGuard} from "angular-auth-oidc-client";
import {HomePageComponent} from "./pages/home-page/home-page.component";
import {AddProductComponent} from "./pages/add-product/add-product.component";
import {RegisterComponent} from "./pages/register/register.component";

export const routes: Routes = [
  {path: '', component: HomePageComponent},
  // Public: a visitor registers before they have an account/token
  {path: 'register', component: RegisterComponent},
  // Logged-out users are sent to the Keycloak login, then back here
  {path: 'add-product', component: AddProductComponent, canActivate: [autoLoginPartialRoutesGuard]}
];
