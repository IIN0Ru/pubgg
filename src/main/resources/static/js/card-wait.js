/*
 * 플레이 카드가 아직 계산 중일 때: 3초마다 서버에 물어보고, 끝났으면 화면을 새로고침한다.
 * 전적 목록은 이미 보이는 상태라, 카드 자리와 상세 분석만 나중에 채워진다.
 * 5분이 지나도 안 끝나면 그만 물어본다 (서버가 다시 켜졌거나 실패한 경우).
 */
(function () {
  var box = document.querySelector('[data-card-wait]');
  if (!box) return;

  var url = '/search/card-status?platform=' + encodeURIComponent(box.dataset.platform)
          + '&nickname=' + encodeURIComponent(box.dataset.nickname);
  var tries = 0;

  function check() {
    tries++;
    fetch(url, { headers: { Accept: 'application/json' } })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (!data.pending) { location.reload(); return; }
        if (tries < 100) setTimeout(check, 3000);
      })
      .catch(function () {
        if (tries < 100) setTimeout(check, 5000);
      });
  }
  setTimeout(check, 3000);
})();
