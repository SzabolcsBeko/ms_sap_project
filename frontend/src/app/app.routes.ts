import {Routes} from '@angular/router';
import {autoLoginPartialRoutesGuard} from "angular-auth-oidc-client";
import {HomePageComponent} from "./pages/home-page/home-page.component";
import {AddProductComponent} from "./pages/add-product/add-product.component";

export const routes: Routes = [
  {path: '', component: HomePageComponent},
  // Logged-out users are sent to the Keycloak login, then back here
  {path: 'add-product', component: AddProductComponent, canActivate: [autoLoginPartialRoutesGuard]}
];
