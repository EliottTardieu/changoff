import { AfterViewInit, Directive, ElementRef, inject, OnDestroy } from '@angular/core';
@Directive({ selector: 'dialog[appModal]', standalone: true })
export class ModalDirective implements AfterViewInit, OnDestroy {
  private readonly element = inject<ElementRef<HTMLDialogElement>>(ElementRef);
  private opener: HTMLElement | null = null;
  ngAfterViewInit() {
    this.opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.element.nativeElement.showModal();
  }
  ngOnDestroy() {
    this.element.nativeElement.close();
    // Angular may detach the dialog before destruction, bypassing native focus restoration.
    if (this.opener?.isConnected) this.opener.focus();
  }
}
@Directive({ selector: '[appAutofocus]', standalone: true })
export class AutofocusDirective implements AfterViewInit {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  ngAfterViewInit() {
    this.element.nativeElement.focus();
  }
}
export function isBackdropClick(event: MouseEvent): boolean {
  if (event.target !== event.currentTarget) return false;
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
  return (
    event.clientX < bounds.left ||
    event.clientX > bounds.right ||
    event.clientY < bounds.top ||
    event.clientY > bounds.bottom
  );
}
export function trapFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return;
  const dialog = event.currentTarget as HTMLElement;
  const items = Array.from(
    dialog.querySelectorAll<HTMLElement>('button:not([disabled]),input,select'),
  );
  const first = items[0],
    last = items.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
