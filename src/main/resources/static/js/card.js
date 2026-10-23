/*
 * 공유 카드의 작은 동작.
 *  "공유 링크 복사": 지금 주소를 복사하고, 됐는지 버튼 글자로 알려준다
 */
(function () {
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
