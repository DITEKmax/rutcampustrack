// Landing theme bootstrap — декодит сохранённую тему из localStorage
// или системную `prefers-color-scheme` и ставит `data-theme` на <html>
// до первого render'а, чтобы избежать flash wrong theme.
// M07 G12 security hot-patch: вынесен из inline <script> для CSP
// script-src 'self' (HIGH-1).
(function () {
  try {
    var stored = localStorage.getItem('ruttrack.theme');
    if (stored !== 'light' && stored !== 'dark') stored = null; // validate
    var theme = stored || (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
    document.documentElement.setAttribute('data-theme', theme);
  } catch (e) {
    document.documentElement.setAttribute('data-theme', 'dark');
  }
})();
