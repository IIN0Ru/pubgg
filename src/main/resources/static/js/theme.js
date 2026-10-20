/*
 * 다크/라이트 전환 버튼.
 * 처음 모드는 <head> 의 fragments/theme :: init 이 이미 정해 둔다. 여기서는 버튼만 처리.
 * 고른 모드는 브라우저에 저장해서 다음에 와도 그대로 (저장이 막힌 브라우저면 이번 화면에서만).
 */
(function () {
  var root = document.documentElement;

  function label(btn) {
    btn.setAttribute('aria-label', root.dataset.theme === 'light' ? '다크 모드로 바꾸기' : '라이트 모드로 바꾸기');
  }

  document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
    label(btn);
    btn.addEventListener('click', function () {
      var next = root.dataset.theme === 'light' ? 'dark' : 'light';
      root.dataset.theme = next;
      try { localStorage.setItem('pubgg.theme', next); } catch (e) {}
      document.querySelectorAll('[data-theme-toggle]').forEach(label);
    });
  });
})();
