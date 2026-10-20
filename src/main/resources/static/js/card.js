/*
 * 공유 카드의 작은 동작.
 *  1) 칭호를 누르면 뜻과 받는 기준이 카드 안에 펼쳐진다 (마우스 툴팁은 폰·키보드에서 못 봐서)
 *  2) "공유 링크 복사": 지금 주소를 복사하고, 됐는지 버튼 글자로 알려준다
 */
(function () {
  document.querySelectorAll('[data-title-toggle]').forEach(function (btn) {
    var note = document.getElementById(btn.getAttribute('aria-controls'));
    if (!note) return;
    btn.addEventListener('click', function () {
      var open = btn.getAttribute('aria-expanded') !== 'true';
      // 한 번에 하나만 펼친다
      document.querySelectorAll('[data-title-toggle]').forEach(function (other) {
        other.setAttribute('aria-expanded', 'false');
        var n = document.getElementById(other.getAttribute('aria-controls'));
        if (n) n.hidden = true;
      });
      btn.setAttribute('aria-expanded', open ? 'true' : 'false');
      note.hidden = !open;
    });
  });

  document.querySelectorAll('[data-copy-link]').forEach(function (btn) {
    var label = btn.textContent;
    btn.addEventListener('click', function () {
      if (!navigator.clipboard) {
        btn.textContent = '주소창의 링크를 복사해 주세요';
        return;
      }
      navigator.clipboard.writeText(location.href).then(function () {
        btn.textContent = '링크를 복사했어요';
        setTimeout(function () { btn.textContent = label; }, 1600);
      }, function () {
        btn.textContent = '복사하지 못했어요. 주소창의 링크를 복사해 주세요';
      });
    });
  });
})();
