import { Injectable, signal } from '@angular/core';
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong.';
}
/** Provide per feature to keep independent forms' notifications and pending requests separate. */
@Injectable()
export class RequestState {
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  clear() {
    this.error.set('');
    this.notice.set('');
  }
  async run(
    operation: () => Promise<void>,
    options: { clearNotice?: boolean; onError?: (error: unknown) => void } = {},
  ): Promise<boolean> {
    if (this.busy()) return false;
    this.busy.set(true);
    this.error.set('');
    if (options.clearNotice) this.notice.set('');
    try {
      await operation();
      return true;
    } catch (error) {
      options.onError?.(error);
      this.error.set(errorMessage(error));
      return false;
    } finally {
      this.busy.set(false);
    }
  }
}
