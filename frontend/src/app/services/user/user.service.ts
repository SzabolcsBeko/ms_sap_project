import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {RegisteredUser, UserRegistration} from "../../model/user-registration";

@Injectable({
  providedIn: 'root'
})
export class UserService {

  constructor(private httpClient: HttpClient) {
  }

  register(request: UserRegistration): Observable<RegisteredUser> {
    return this.httpClient.post<RegisteredUser>('http://localhost:9000/api/user/register', request);
  }
}
