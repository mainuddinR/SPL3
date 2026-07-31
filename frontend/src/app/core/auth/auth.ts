import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TokenStorage } from './token-storage';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

const AUTH_API = 'http://localhost:8080/api/auth/';

@Injectable({
  providedIn: 'root',
})
export class Auth {
  private http = inject(HttpClient);
  private tokenStorage = inject(TokenStorage);
  
  private currentUserSubject = new BehaviorSubject<any>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor() {
    this.checkLoginStatus();
  }

  checkLoginStatus() {
    if (this.tokenStorage.getToken()) {
      this.fetchUser().subscribe();
    }
  }

  fetchUser(): Observable<any> {
    return this.http.get(AUTH_API + 'me').pipe(
      tap(user => this.currentUserSubject.next(user))
    );
  }

  logout(): void {
    this.tokenStorage.signOut();
    this.currentUserSubject.next(null);
  }

  isLoggedIn(): boolean {
    return !!this.tokenStorage.getToken();
  }
}
