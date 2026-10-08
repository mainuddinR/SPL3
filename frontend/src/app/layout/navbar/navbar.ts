import { Component, inject, signal } from '@angular/core';
import { Auth } from '../../core/auth/auth';
import { CommonModule } from '@angular/common';
import { SidebarService } from '../sidebar.service';
import { NotificationBell } from './notification-bell';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, NotificationBell],
  templateUrl: './navbar.html'
})
export class Navbar {
  auth = inject(Auth);
  sidebarService = inject(SidebarService);
  notifications = inject(NotificationService);
  
  isProfileOpen = signal(false);

  toggleSidebar() {
    this.sidebarService.toggle();
  }

  toggleProfile() {
    this.isProfileOpen.update(v => !v);
  }

  logout() {
    this.notifications.clear();
    this.auth.logout();
    window.location.href = '/login';
  }
}
