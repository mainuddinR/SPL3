import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { TokenStorage } from './token-storage';

export const authGuard: CanActivateFn = (route, state) => {
  const tokenStorage = inject(TokenStorage);
  const router = inject(Router);

  if (tokenStorage.getToken()) {
    return true;
  }
  
  router.navigate(['/login']);
  return false;
};
