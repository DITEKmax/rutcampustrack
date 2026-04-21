// Landing main — theme toggle + GSAP animations.
// M07 G12 security hot-patch: вынесен из inline <script> в дочернее
// script-src 'self' (HIGH-1 от security-auditor).

// ============================================================
// Theme toggle — sync with localStorage + prefers-color-scheme
// ============================================================
(function () {
  var root = document.documentElement;
  var btn = document.getElementById('theme-toggle');
  var STORAGE_KEY = 'ruttrack.theme';
  var mq = window.matchMedia('(prefers-color-scheme: light)');
  var userOverride = !!localStorage.getItem(STORAGE_KEY);

  function apply(theme, persist) {
    root.classList.add('theme-transitioning');
    root.setAttribute('data-theme', theme);
    if (persist) {
      localStorage.setItem(STORAGE_KEY, theme);
      userOverride = true;
    }
    setTimeout(function () { root.classList.remove('theme-transitioning'); }, 320);
  }

  btn && btn.addEventListener('click', function () {
    var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    apply(next, true);
  });

  // Follow OS change until user explicitly toggles
  if (mq.addEventListener) {
    mq.addEventListener('change', function (e) {
      if (!userOverride) apply(e.matches ? 'light' : 'dark', false);
    });
  }
})();

// ============================================================
// GSAP animations — only when loaded
// ============================================================
window.addEventListener('load', function () {
  if (typeof gsap === 'undefined') return;
  if (typeof ScrollTrigger !== 'undefined') gsap.registerPlugin(ScrollTrigger);

  var mm = gsap.matchMedia();
  mm.add('(prefers-reduced-motion: no-preference)', function () {
    // Hero headline — line reveal
    gsap.to('.hero__title .line > span', {
      y: 0,
      duration: 0.9,
      ease: 'power4.out',
      stagger: 0.12,
      delay: 0.1
    });

    // Hero supporting content
    gsap.to('.hero__eyebrow', { opacity: 1, y: 0, duration: 0.6, delay: 0.05, ease: 'power2.out' });
    gsap.to('.hero__subtitle', { opacity: 1, y: 0, duration: 0.7, delay: 0.45, ease: 'power2.out' });
    gsap.to('.hero__cta', { opacity: 1, y: 0, duration: 0.6, delay: 0.6, ease: 'power2.out' });
    gsap.to('.hero__meta', { opacity: 1, y: 0, duration: 0.6, delay: 0.75, ease: 'power2.out' });
    gsap.to('.hero__visual', {
      opacity: 1, y: 0, scale: 1, duration: 1.0, delay: 0.35, ease: 'power3.out',
      clearProps: 'transform'
    });

    // CountUp for hero meta numbers
    document.querySelectorAll('[data-count]').forEach(function (el) {
      var target = parseFloat(el.getAttribute('data-count'));
      var suffix = '';
      var raw = el.textContent.trim();
      var match = raw.match(/%|\/\d+/);
      if (match) suffix = match[0];

      var obj = { v: 0 };
      gsap.to(obj, {
        v: target,
        duration: 1.6,
        ease: 'power2.out',
        delay: 0.6,
        onUpdate: function () {
          var val = Math.round(obj.v);
          if (el.classList.contains('value')) {
            el.innerHTML = val + '<span style="font-size:60%;">%</span>';
          } else {
            el.textContent = val;
          }
        }
      });
    });

    // Reveal on scroll — batched
    if (typeof ScrollTrigger !== 'undefined') {
      ScrollTrigger.batch('.reveal:not(.hero__eyebrow):not(.hero__subtitle):not(.hero__cta):not(.hero__meta):not(.hero__visual)', {
        start: 'top 88%',
        once: true,
        onEnter: function (els) {
          gsap.to(els, {
            opacity: 1,
            y: 0,
            duration: 0.7,
            ease: 'power3.out',
            stagger: 0.09
          });
        }
      });

      // Parallax on hero routes
      gsap.to('.hero__routes', {
        yPercent: 12,
        ease: 'none',
        scrollTrigger: {
          trigger: '.hero',
          start: 'top top',
          end: 'bottom top',
          scrub: true
        }
      });
    }

    // Feature card hover — subtle light follow
    document.querySelectorAll('.feature-card').forEach(function (card) {
      card.addEventListener('pointermove', function (e) {
        var rect = card.getBoundingClientRect();
        card.style.setProperty('--mx', ((e.clientX - rect.left) / rect.width * 100) + '%');
        card.style.setProperty('--my', ((e.clientY - rect.top) / rect.height * 100) + '%');
      });
    });

    // ---- ARCHITECTURE FLOW (Phase 57 / LAND-v9-02) ----
    if (typeof ScrollTrigger !== 'undefined') {
      var isArchDesktop = window.matchMedia('(min-width: 1024px)').matches;
      if (isArchDesktop) {
        gsap.set('.arch-step', { opacity: 0, y: 40 });
        gsap.set('.arch-arrow', { scaleX: 0 });
        var archTL = gsap.timeline({
          scrollTrigger: {
            trigger: '#architecture-flow',
            start: 'top top',
            end: '+=500%',
            scrub: 0.6,
            pin: true,
            pinSpacing: true,
            anticipatePin: 1
          }
        });
        for (var archI = 1; archI <= 6; archI++) {
          archTL.to('.arch-step[data-step="' + archI + '"]', { opacity: 1, y: 0, duration: 1, ease: 'power2.out' });
          if (archI < 6) {
            archTL.to('.arch-arrow[data-to="' + (archI + 1) + '"]', { scaleX: 1, duration: 0.6, ease: 'power1.inOut' }, '-=0.3');
          }
        }
      } else {
        gsap.set('.arch-step', { opacity: 0, y: 24 });
        gsap.set('.arch-arrow', { scaleX: 1 });
        ScrollTrigger.batch('.arch-step', {
          start: 'top 85%',
          once: true,
          onEnter: function (els) {
            gsap.to(els, { opacity: 1, y: 0, stagger: 0.1, duration: 0.6, ease: 'power3.out' });
          }
        });
      }
    }
  });

  // Reduced motion — show everything instantly (M07 G2, P2-7B/3)
  mm.add('(prefers-reduced-motion: reduce)', function () {
    gsap.set('.hero__title .line > span', { y: 0 });
    gsap.set('.hero__eyebrow, .hero__subtitle, .hero__cta, .hero__meta, .hero__visual, .reveal', { opacity: 1, y: 0 });
    gsap.set('.arch-step, .arch-arrow', { opacity: 1, y: 0, scaleX: 1 });

    // SMIL <animateMotion> в hero SVG — CSS override выше не останавливает
    // SMIL, поэтому дёргаем нативный API. matchMedia (gsap.matchMedia)
    // вызовет cleanup → unpause если пользователь отключит reduce live.
    var pausedSvgs = [];
    document.querySelectorAll('svg').forEach(function (svg) {
      if (typeof svg.pauseAnimations === 'function') {
        try { svg.pauseAnimations(); pausedSvgs.push(svg); } catch (_) { /* jsdom */ }
      }
    });
    return function cleanup() {
      pausedSvgs.forEach(function (svg) {
        if (typeof svg.unpauseAnimations === 'function') {
          try { svg.unpauseAnimations(); } catch (_) { /* noop */ }
        }
      });
    };
  });
});
