// Flag: JS is enabled — allow initial-state styles.
// Execute as early as possible after body start — inline-script
// заменён на отдельный файл для CSP script-src 'self' (M07 G12, HIGH-1).
document.body.classList.remove('no-js');
document.body.classList.add('js');
