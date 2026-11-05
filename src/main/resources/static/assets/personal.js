// Общие скрипты личных страниц kuklin.dev: тема, подсветка меню, копирование контактов, мобильное меню.
// Начальная тема выставляется инлайн-скриптом в <head> страницы — чтобы не мигала светлая тема.
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const htmlEl = document.documentElement;

  // Год в футере
  const year = $('#year');
  if (year) year.textContent = new Date().getFullYear();

  // Переключатель темы
  const THEME_KEY = 'dk_theme', themeBtn = $('#themeToggle');
  function setTheme(t, persist = true) {
    htmlEl.setAttribute('data-theme', t);
    themeBtn?.setAttribute('aria-pressed', String(t === 'dark'));
    if (persist) { try { localStorage.setItem(THEME_KEY, t); } catch (e) {} }
  }
  setTheme(htmlEl.getAttribute('data-theme') || 'light', false);
  themeBtn?.addEventListener('click', () => setTheme(htmlEl.getAttribute('data-theme') === 'light' ? 'dark' : 'light'));

  // Подсветка активного пункта в шапке. Секции берём из ссылок меню; скролл к якорю — нативный (scroll-padding-top в CSS)
  const headerH = parseInt(getComputedStyle(htmlEl).getPropertyValue('--header-h')) || 60;
  const navLinks = $$('header a[data-nav]');
  const linkByHash = new Map(navLinks.map(a => [a.getAttribute('href'), a]));
  const setActive = link => { navLinks.forEach(x => x.classList.remove('active')); link?.classList.add('active'); };
  const io = new IntersectionObserver(entries => {
    entries.forEach(en => { const link = linkByHash.get('#' + en.target.id); if (link && en.isIntersecting) setActive(link); });
  }, { rootMargin: `-${headerH + 24}px 0px -60% 0px`, threshold: 0.1 });
  linkByHash.forEach((_, hash) => { const s = $(hash); if (s) io.observe(s); });
  navLinks.forEach(a => a.addEventListener('click', () => setActive(a)));

  // Долистали до самого низа — подсвечиваем последний пункт (обычно «Контакты»)
  addEventListener('scroll', () => {
    if (Math.ceil(scrollY + innerHeight) >= document.documentElement.scrollHeight) setActive(navLinks[navLinks.length - 1]);
  }, { passive: true });

  // Копирование контактов. Иконку не затираем — меняем на галочку и возвращаем
  const CHECK_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5 10 17l9-10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  document.addEventListener('click', e => {
    const cp = e.target.closest('.ct-copy');
    if (!cp || !navigator.clipboard) return;
    navigator.clipboard.writeText(cp.getAttribute('data-copy') || '').then(() => {
      if (!cp.dataset.icon) cp.dataset.icon = cp.innerHTML;
      clearTimeout(cp._copyTimer);
      cp.innerHTML = CHECK_ICON;
      cp.classList.add('copied');
      cp.title = 'Скопировано';
      cp._copyTimer = setTimeout(() => { cp.innerHTML = cp.dataset.icon; cp.classList.remove('copied'); cp.removeAttribute('title'); }, 1200);
    });
  });

  // Мобильное меню: пункты собираем из меню шапки
  const menuBtn = $('#menuToggle'), drawer = $('#mobileDrawer');
  if (!menuBtn || !drawer) return;
  const list = $('ul', drawer);
  list.innerHTML = navLinks.map(a =>
    `<li><a class="drawer-link" href="${a.getAttribute('href')}">${a.textContent.trim()} <span aria-hidden="true">→</span></a></li>`
  ).join('');
  const links = $$('.drawer-link', drawer);

  const open = () => {
    drawer.hidden = false;
    // на следующий кадр — чтобы сработали CSS-переходы
    requestAnimationFrame(() => {
      drawer.classList.add('open');
      menuBtn.setAttribute('aria-expanded', 'true');
      drawer.setAttribute('aria-hidden', 'false');
      document.body.classList.add('no-scroll');
      links[0]?.focus({ preventScroll: true });
    });
  };
  const close = () => {
    drawer.classList.remove('open');
    menuBtn.setAttribute('aria-expanded', 'false');
    drawer.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('no-scroll');
    setTimeout(() => { drawer.hidden = true; }, 220);
  };

  menuBtn.addEventListener('click', () => drawer.classList.contains('open') ? close() : open());
  $('.drawer-backdrop', drawer).addEventListener('click', close);
  addEventListener('keydown', e => { if (e.key === 'Escape' && drawer.classList.contains('open')) { close(); menuBtn.focus(); } });
  // Пункт меню: закрыть панель и подсветить пункт в шапке, дальше — нативный переход по якорю
  links.forEach(a => a.addEventListener('click', () => { close(); setActive(linkByHash.get(a.getAttribute('href'))); }));
  addEventListener('resize', () => { if (innerWidth > 640 && drawer.classList.contains('open')) close(); }, { passive: true });
})();
