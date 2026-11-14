/** Carrega, uma única vez, o script do Google ou da Apple. */
const carregados = new Map<string, Promise<void>>();

export function carregarScript(src: string): Promise<void> {
  if (!carregados.has(src)) {
    carregados.set(src, new Promise<void>((ok, erro) => {
      const s = document.createElement('script');
      s.src = src; s.async = true;
      s.onload = () => ok();
      s.onerror = () => { carregados.delete(src); erro(new Error('Falha ao carregar ' + src)); };
      document.head.appendChild(s);
    }));
  }
  return carregados.get(src)!;
}
export const GOOGLE_JS = 'https://accounts.google.com/gsi/client';
export const APPLE_JS = 'https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js';
