/** Request body for POST /api/user/register (confirmPassword is frontend-only, never sent). */
export interface UserRegistration {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
}

/** 201 response from the user-service. */
export interface RegisteredUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
}
