import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class SidebarService {
  isOpen = signal<boolean>(true);

  toggle() {
    this.isOpen.update(v => !v);
  }
}
