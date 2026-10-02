import { Pipe, PipeTransform } from '@angular/core';
import { FR } from './locales/fr';
// Stored WOD instructions predate localization. Match their versioned source templates,
// rather than persisting translated content or altering user-authored notes/titles.
const storedMessages = Object.keys(FR)
  .filter((k) => /^(For |Complete |Only |Coverage in this WOD: )/.test(k) && k.includes('{0}'))
  .map((key) => ({
    key,
    pattern: new RegExp(
      '^' +
        key
          .split(/\{\d+\}/)
          .map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
          .join('(.+?)') +
        '$',
    ),
  }));

export const language: 'en' | 'fr' = location.pathname.split('/')[1] === 'fr' ? 'fr' : 'en';
document.documentElement.lang = language;
document.title =
  language === 'fr'
    ? 'Time to Chang · Votre suivi sportif'
    : 'Time to Chang · Your training progress';
export function translate(value: unknown, ...args: unknown[]): string {
  const key = String(value ?? '');
  const trimmed = key.trim();
  let message =
    language === 'fr'
      ? (FR[key] ?? (FR[trimmed] !== undefined ? key.replace(trimmed, FR[trimmed]) : key))
      : key;
  if (language === 'fr' && message === key && !FR[key]) {
    for (const template of storedMessages) {
      const match = key.match(template.pattern);
      if (match) {
        const values = match.slice(1);
        if (template.key.startsWith('Coverage'))
          values[0] = values[0]
            .split(', ')
            .map((v) => FR[v] ?? v)
            .join(', ');
        return translate(template.key, ...values);
      }
    }
    if (key.startsWith('Unknown selection for ')) return 'Sélection inconnue. Vérifiez vos choix.';
  }
  if (language === 'fr' && !FR[key]) {
    const rank = key.match(/^(Bronze|Silver|Gold|Platinum|Emerald|Diamond|Master|Chang) ([123])$/);
    if (rank) message = (FR[rank[1]] ?? rank[1]) + ' ' + rank[2];
  }
  return message.replace(/\{(\d+)\}/g, (match, index) =>
    args[Number(index)] === undefined ? match : String(args[Number(index)] ?? ''),
  );
}
@Pipe({ name: 't', standalone: true })
export class TranslatePipe implements PipeTransform {
  transform(value: unknown, ...args: unknown[]): string {
    return translate(value, ...args);
  }
}
export function localizedPath(page: string) {
  return `/${language}/${page}`;
}
export function switchLanguage(next: string) {
  if (next !== 'en' && next !== 'fr') return;
  const url = new URL(location.href);
  url.pathname = url.pathname.replace(/^\/(en|fr)(?=\/|$)/, '/' + next);
  location.assign(url.pathname + url.search + url.hash);
}
