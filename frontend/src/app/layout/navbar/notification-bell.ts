import { Component, HostListener, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.service';

@Component({ selector: 'app-notification-bell', standalone: true, imports: [CommonModule], templateUrl: './notification-bell.html' })
export class NotificationBell {
  notifications = inject(NotificationService);
  private router = inject(Router);
  open = signal(false);

  toggle(event: Event) {
    event.stopPropagation();
    this.open.update(value => !value);
    if (this.open()) this.notifications.refresh();
  }
  @HostListener('document:click') closeOutside() { this.open.set(false); }
  @HostListener('document:keydown.escape') closeEscape() { this.open.set(false); }
  stop(event: Event) { event.stopPropagation(); }
  navigate(id: number, prId: number) {
    this.notifications.markRead(id);
    this.open.set(false);
    this.router.navigate(['/dashboard/pulls', prId]);
  }
  relativeTime(value: string) {
    const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes} min ago`;
    if (minutes < 1440) return `${Math.floor(minutes / 60)} hours ago`;
    if (minutes < 2880) return 'Yesterday';
    return `${Math.floor(minutes / 1440)} days ago`;
  }
}
