/*
 * 검색·전적 갱신 중 표시.
 *
 * 처음 검색하면 서버가 최근 14일 경기를 모두 받아야 해서 오래 걸린다.
 * 그동안 화면이 그대로면 고장 난 것처럼 보이고, 버튼을 여러 번 눌러 같은 요청이 쌓인다.
 * 그래서 폼을 보내는 순간:
 *   - 버튼을 막고 글자를 "분석 중…"으로 바꾼다 (data-loading-text)
 *   - 안내 문구가 있으면 보여준다 (data-loading-status)
 *   - 두 번째 제출은 막는다
 *
 * 사용법: <form data-loading> 안에 <button data-loading-button data-loading-text="분석 중…">
 * 브라우저 뒤로 가기로 돌아오면 원래대로 되돌린다 (pageshow).
 */
(function () {
  var forms = document.querySelectorAll('form[data-loading]');

  function reset(form) {
    form.removeAttribute('aria-busy');
    form.dataset.sending = '';
    var btn = form.querySelector('[data-loading-button]');
    if (btn && btn.dataset.originalLabel !== undefined) {
      if (btn.dataset.loadingText) btn.textContent = btn.dataset.originalLabel;
      btn.disabled = btn.dataset.originalDisabled === 'true';
    }
    var status = form.querySelector('[data-loading-status]');
    if (status) status.hidden = true;
  }

  forms.forEach(function (form) {
    form.addEventListener('submit', function (e) {
      if (form.dataset.sending === 'true') {   // 이미 보내는 중이면 두 번째는 막기
        e.preventDefault();
        return;
      }
      form.dataset.sending = 'true';
      form.setAttribute('aria-busy', 'true');

      var btn = form.querySelector('[data-loading-button]');
      if (btn) {
        btn.dataset.originalLabel = btn.textContent;
        btn.dataset.originalDisabled = btn.disabled ? 'true' : 'false';
        if (btn.dataset.loadingText) btn.textContent = btn.dataset.loadingText;
        // 지금 막으면 이번 제출이 취소되는 브라우저가 있어서, 한 박자 뒤에 막는다
        setTimeout(function () { btn.disabled = true; }, 0);
      }
      var status = form.querySelector('[data-loading-status]');
      if (status) status.hidden = false;
    });
  });

  window.addEventListener('pageshow', function (e) {
    if (e.persisted) forms.forEach(reset);
  });
})();
