import {Component, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from "@angular/core/rxjs-interop";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";
import {NgIf} from "@angular/common";
import {RouterLink} from "@angular/router";
import {OidcSecurityService} from "angular-auth-oidc-client";
import {HttpErrorResponse} from "@angular/common/http";
import {take} from "rxjs";
import {UserService} from "../../services/user/user.service";
import {UserRegistration} from "../../model/user-registration";
import {passwordMatchValidator} from "../../validators/password-match.validator";

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent implements OnInit {
  registerForm: FormGroup;
  private readonly userService = inject(UserService);
  private readonly oidcSecurityService = inject(OidcSecurityService);

  isAuthenticated = false;
  isSubmitting = false;
  successMessage = '';
  errorMessage = '';

  constructor(private fb: FormBuilder) {
    // Rules mirror the backend @Valid and the Keycloak password policy (§4.1)
    this.registerForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50),
        Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]],
      confirmPassword: ['', [Validators.required]]
    }, {validators: passwordMatchValidator});

    // A server error (e.g. 409 "username taken") describes the values that were submitted;
    // once the user edits the form it is stale, so drop the banner. Field-level "taken"
    // errors already clear themselves because editing re-runs the control's validators.
    this.registerForm.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.errorMessage = '';
    });
  }

  ngOnInit(): void {
    this.oidcSecurityService.isAuthenticated$.pipe(take(1)).subscribe(
      ({isAuthenticated}) => this.isAuthenticated = isAuthenticated
    );
  }

  onSubmit(): void {
    this.successMessage = '';
    this.errorMessage = '';
    if (this.registerForm.invalid || this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;

    const request: UserRegistration = {
      username: this.registerForm.get('username')?.value,
      email: this.registerForm.get('email')?.value,
      firstName: this.registerForm.get('firstName')?.value,
      lastName: this.registerForm.get('lastName')?.value,
      password: this.registerForm.get('password')?.value
    };

    this.userService.register(request).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.successMessage = 'Account created. You can now log in.';
        this.registerForm.reset();
      },
      error: (err: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.handleError(err);
      }
    });
  }

  private handleError(err: HttpErrorResponse): void {
    const problem = err.error;
    if (err.status === 409 && problem?.errors) {
      // Attach "taken" to the matching control
      if (problem.errors['username']) {
        this.username?.setErrors({taken: true});
      }
      if (problem.errors['email']) {
        this.email?.setErrors({taken: true});
      }
      this.errorMessage = problem.detail ?? 'That account already exists.';
    } else if (err.status === 400 && problem?.code === 'WEAK_PASSWORD') {
      this.password?.setErrors({weak: true});
      this.errorMessage = 'Please choose a stronger password.';
    } else if (err.status === 400 && problem?.errors) {
      this.errorMessage = 'Please correct the highlighted fields.';
    } else {
      this.errorMessage = 'Registration is temporarily unavailable, please try again later.';
    }
  }

  login(): void {
    this.oidcSecurityService.authorize();
  }

  get username() {
    return this.registerForm.get('username');
  }

  get email() {
    return this.registerForm.get('email');
  }

  get firstName() {
    return this.registerForm.get('firstName');
  }

  get lastName() {
    return this.registerForm.get('lastName');
  }

  get password() {
    return this.registerForm.get('password');
  }

  get confirmPassword() {
    return this.registerForm.get('confirmPassword');
  }
}
