import { Component, inject, signal } from '@angular/core';
import { Auth } from '../../core/auth/auth';
import { CommonModule } from '@angular/common';
import { SidebarService } from '../sidebar.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './navbar.html'
})
export class Navbar {
  auth = inject(Auth);
  sidebarService = inject(SidebarService);
  
  isProfileOpen = signal(false);

  toggleSidebar() {
    this.sidebarService.toggle();
  }

  toggleProfile() {
    this.isProfileOpen.update(v => !v);
  }

  logout() {
    this.auth.logout();
    window.location.href = '/login';
  }
}
