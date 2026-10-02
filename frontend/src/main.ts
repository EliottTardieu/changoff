import { LOCALE_ID } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { AppComponent } from './app/app.component';
import { language } from './core/i18n/i18n';
import { RequestState } from './core/feedback/request-state';
registerLocaleData(localeFr);
bootstrapApplication(AppComponent, {
  providers: [
    RequestState,
    { provide: LOCALE_ID, useValue: language === 'fr' ? 'fr-FR' : 'en-US' },
  ],
}).catch(console.error);
