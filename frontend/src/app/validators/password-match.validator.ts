import {AbstractControl, ValidationErrors, ValidatorFn} from "@angular/forms";

/**
 * Group validator: sets {passwordMismatch: true} on the group when password !== confirmPassword.
 * Attach to the form group, not to a single control.
 */
export const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  if (!password || !confirmPassword) {
    return null;
  }
  return password === confirmPassword ? null : {passwordMismatch: true};
};
