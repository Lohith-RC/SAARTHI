/* SAARTHI first-run experience: quiet welcome card + plain-language tooltips.
   Loaded alongside app.js; defines globals used by the header buttons. */
(function () {
  'use strict';

  var DISMISS_KEY = 'saarthi_first_run_dismissed';

  function showFirstRunWelcome() {
    var backdrop = document.getElementById('firstRunBackdrop');
    if (backdrop) backdrop.style.display = 'flex';
  }

  function hideFirstRunWelcome() {
    var backdrop = document.getElementById('firstRunBackdrop');
    if (backdrop) backdrop.style.display = 'none';
  }

  function dismissFirstRun() {
    hideFirstRunWelcome();
    try { localStorage.setItem(DISMISS_KEY, 'true'); } catch (e) { /* private mode */ }
  }

  /* Primary CTA: close the card and invite the user to touch the sliders. */
  function tryControls() {
    dismissFirstRun();
    var panel = document.getElementById('panelLeft');
    if (panel && panel.classList.contains('collapsed') && typeof togglePanel === 'function') {
      togglePanel('left');
    }
    var rig = document.getElementById('simRig');
    if (rig) {
      setTimeout(function () {
        rig.scrollIntoView({ behavior: 'smooth', block: 'center' });
        rig.classList.remove('attention-pulse');
        void rig.offsetWidth; /* restart the animation */
        rig.classList.add('attention-pulse');
        setTimeout(function () { rig.classList.remove('attention-pulse'); }, 5400);
      }, 300);
    }
    if (typeof showToast === 'function') {
      showToast('Simulation Rig', 'Drag a slider to push live telemetry into the chamber and watch the HUD react.', 'info', 4200);
    }
  }

  /* Secondary CTA: hand over to the existing guided walkthrough. */
  function takeGuidedTour() {
    dismissFirstRun();
    if (typeof startOnboardingTour === 'function') startOnboardingTour(true);
  }

  /* ---- Plain-language tooltips (delegated, single element) ---- */
  var tipEl = null;
  var tipTimer = null;

  function getTip() {
    if (!tipEl) {
      tipEl = document.createElement('div');
      tipEl.className = 'saarthi-tooltip';
      tipEl.setAttribute('role', 'tooltip');
      document.body.appendChild(tipEl);
    }
    return tipEl;
  }

  function showTip(target) {
    var text = target.getAttribute('data-tip');
    if (!text) return;
    var tip = getTip();
    tip.textContent = text;
    tip.classList.add('visible');
    positionTip(target, tip);
  }

  function positionTip(target, tip) {
    var r = target.getBoundingClientRect();
    var tw = tip.offsetWidth;
    var th = tip.offsetHeight;
    var x = r.left + r.width / 2 - tw / 2;
    var y = r.top - th - 10;
    if (x < 8) x = 8;
    if (x + tw > window.innerWidth - 8) x = window.innerWidth - tw - 8;
    if (y < 8) y = r.bottom + 10;
    tip.style.left = Math.round(x) + 'px';
    tip.style.top = Math.round(y) + 'px';
  }

  function hideTip() {
    if (tipEl) tipEl.classList.remove('visible');
  }

  document.addEventListener('mouseover', function (e) {
    var t = e.target && e.target.closest ? e.target.closest('[data-tip]') : null;
    if (!t) return;
    clearTimeout(tipTimer);
    tipTimer = setTimeout(function () { showTip(t); }, 220);
  });

  document.addEventListener('mouseout', function (e) {
    var t = e.target && e.target.closest ? e.target.closest('[data-tip]') : null;
    if (!t) return;
    clearTimeout(tipTimer);
    hideTip();
  });

  document.addEventListener('focusin', function (e) {
    var t = e.target && e.target.closest ? e.target.closest('[data-tip]') : null;
    if (t) showTip(t);
  });

  document.addEventListener('focusout', function (e) {
    var t = e.target && e.target.closest ? e.target.closest('[data-tip]') : null;
    if (t) hideTip();
  });

  window.addEventListener('scroll', function () { hideTip(); }, true);
  window.addEventListener('resize', function () {
    if (tipEl && tipEl.classList.contains('visible')) hideTip();
  });

  /* Expose for inline onclick handlers in index.html */
  window.showFirstRunWelcome = showFirstRunWelcome;
  window.dismissFirstRun = dismissFirstRun;
  window.tryControls = tryControls;
  window.takeGuidedTour = takeGuidedTour;
})();
