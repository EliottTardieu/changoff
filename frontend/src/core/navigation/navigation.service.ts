import { Injectable, signal } from '@angular/core';
import { localizedPath, language } from '../i18n/i18n';
export type Page = 'overview' | 'history' | 'ranks' | 'cardio' | 'wods' | 'profile';
export type WodTab = 'generate' | 'history' | 'statistics';
const PAGES: readonly string[] = ['overview', 'history', 'ranks', 'cardio', 'wods', 'profile'];
const WOD_TABS: readonly string[] = ['generate', 'history', 'statistics'];
@Injectable({ providedIn: 'root' })
export class NavigationService {
  readonly page = signal<Page>(this.readPage());
  readPage(): Page {
    const page = location.pathname.split('/')[2];
    return PAGES.includes(page) ? (page as Page) : 'overview';
  }
  readWodTab(): WodTab {
    const tab = location.pathname.split('/')[3];
    return WOD_TABS.includes(tab) ? (tab as WodTab) : 'generate';
  }
  go(page: Page, replace = false) {
    this.page.set(page);
    this.write(localizedPath(page) + (page === 'wods' ? '/' + this.readWodTab() : ''), replace);
  }
  wodTab(tab: WodTab) {
    this.write(`/${language}/wods/${tab}`);
  }
  onPopState(listener: () => void): () => void {
    window.addEventListener('popstate', listener);
    return () => window.removeEventListener('popstate', listener);
  }
  private write(path: string, replace = false) {
    history[replace ? 'replaceState' : 'pushState']({}, '', path + location.search + location.hash);
  }
}
