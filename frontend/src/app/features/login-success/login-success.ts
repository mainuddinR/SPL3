import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TokenStorage } from '../../core/auth/token-storage';
import { Auth } from '../../core/auth/auth';

@Component({
  selector: 'app-login-success',
  standalone: true,
  imports: [],
  templateUrl: './login-success.html',
  styleUrl: './login-success.css'
})
export class LoginSuccess implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private tokenStorage = inject(TokenStorage);
  private auth = inject(Auth);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      // URL fragments are not sent to HTTP servers or their access logs.
      const fragment = new URLSearchParams(window.location.hash.substring(1));
      const token = fragment.get('token') || params['token'];
      if (token) {
        window.history.replaceState(window.history.state, '', '/login/success');
        this.tokenStorage.saveToken(token);
        this.auth.checkLoginStatus();
        this.router.navigate(['/dashboard']);
      } else {
        this.router.navigate(['/login']);
      }
    });
  }
}
