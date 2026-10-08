import { Component, ElementRef, HostListener, Input, inject } from '@angular/core';

@Component({
  selector: 'app-info-popover', standalone: true,
  template: `
    <button type="button" class="info-control" [attr.aria-label]="label"
      [attr.aria-expanded]="open" [attr.aria-controls]="id" [attr.aria-describedby]="open ? id : null"
      (mouseenter)="show()" (focus)="show()" (blur)="close()" (click)="show()">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
        <circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7v1"/>
      </svg>
    </button>
    <div class="info-popover" [id]="id" [hidden]="!open" [style.left.px]="left" [style.top.px]="top" [style.max-height.px]="maxHeight"
      [attr.role]="'note'" [attr.aria-label]="label"><ng-content /></div>
  `,
  host: { '(mouseleave)': 'closeUnlessFocused()' }
})
export class InfoPopover {
  private static nextId = 0;
  private host = inject(ElementRef<HTMLElement>);
  @Input({ required: true }) label = '';
  readonly id = `info-popover-${InfoPopover.nextId++}`;
  open = false;
  left = 0;
  top = 0;
  maxHeight = 240;
  show() {
    const rect = this.host.nativeElement.getBoundingClientRect();
    const width = Math.min(272, window.innerWidth - 24);
    this.left = Math.max(12, Math.min(rect.left, window.innerWidth - width - 12));
    this.top = Math.min(rect.bottom + 8, window.innerHeight - 120);
    this.maxHeight = Math.max(80, window.innerHeight - this.top - 12);
    this.open = true;
  }
  close() { this.open = false; }
  closeUnlessFocused() {
    if (!this.host.nativeElement.contains(document.activeElement)) this.close();
  }
  @HostListener('document:click', ['$event']) outside(event: MouseEvent) {
    if (!this.host.nativeElement.contains(event.target as Node)) this.close();
  }
  @HostListener('document:keydown.escape') escape() { this.close(); }
  @HostListener('window:resize') resize() { this.close(); }
}
