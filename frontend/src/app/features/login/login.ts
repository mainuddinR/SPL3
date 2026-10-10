import { API_ORIGIN } from '../../core/config/api-origin';
import { Component } from '@angular/core';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  loginWithGithub() {
    window.location.href = `${API_ORIGIN}/oauth2/authorization/github`;
  }
}
