/*
 * 경기 목록: 필터(경기 종류, 시점) + 더 보기.
 *
 * 서버는 14일 전체 경기를 한 번에 그려 보내고, 여기서는 보이기/숨기기만 한다.
 * (경기 수가 많아야 200개라 화면에서 거르는 게 서버를 다시 부르는 것보다 빠르다)
 * 자바스크립트가 꺼져 있으면 필터 없이 전체 경기가 다 보인다.
 */
(function () {
  var PAGE = 20;   // 한 번에 보여줄 경기 수

  var list = document.querySelector('[data-match-list]');
  if (!list) return;

  var items = Array.prototype.slice.call(list.querySelectorAll('.match'));
  var countEl = list.querySelector('[data-match-count]');
  var emptyEl = list.querySelector('[data-match-empty]');
  var moreBtn = list.querySelector('[data-match-more]');
  var summaryEl = list.querySelector('[data-match-summary]');

  var filter = { type: 'all', pov: 'all' };
  var shown = PAGE;

  function matches(item) {
    return (filter.type === 'all' || item.dataset.type === filter.type)
        && (filter.pov === 'all' || item.dataset.pov === filter.pov);
  }

  function render() {
    var hits = items.filter(matches);
    items.forEach(function (item) { item.hidden = true; });
    hits.slice(0, shown).forEach(function (item) { item.hidden = false; });

    countEl.textContent = hits.length;
    emptyEl.hidden = hits.length > 0;
    summarize(hits);

    var left = hits.length - shown;
    moreBtn.hidden = left <= 0;
    if (left > 0) {
      moreBtn.textContent = '더 보기 (' + left + '경기 남음)';
    }
  }

  // "42판 · 평균 #8.2 · 치킨 2 · TOP 10 40%"
  function summarize(hits) {
    if (!summaryEl) return;
    var ranks = hits.map(function (item) { return Number(item.dataset.rank); })
        .filter(function (r) { return r > 0; });
    if (ranks.length === 0) {
      summaryEl.textContent = '';
      return;
    }
    var sum = ranks.reduce(function (a, b) { return a + b; }, 0);
    var wins = ranks.filter(function (r) { return r === 1; }).length;
    var top10 = ranks.filter(function (r) { return r <= 10; }).length;
    summaryEl.textContent = ranks.length + '판 · 평균 #' + (sum / ranks.length).toFixed(1) +
        ' · 치킨 ' + wins + ' · TOP 10 ' + Math.round(top10 * 100 / ranks.length) + '%';
  }

  // 경기 시각을 "방금 전 / 3시간 전 / 2일 전"으로. 정확한 시각은 title(마우스 올림)에 그대로 남김
  function relative(iso) {
    var t = Date.parse(iso);
    if (isNaN(t)) return null;
    var min = Math.floor((Date.now() - t) / 60000);
    if (min < 1) return '방금 전';
    if (min < 60) return min + '분 전';
    var hour = Math.floor(min / 60);
    if (hour < 24) return hour + '시간 전';
    return Math.floor(hour / 24) + '일 전';
  }

  list.querySelectorAll('[data-relative-time]').forEach(function (el) {
    var text = relative(el.getAttribute('datetime'));
    if (text) el.textContent = text;
  });

  list.querySelectorAll('[data-filter]').forEach(function (group) {
    group.addEventListener('click', function (e) {
      var btn = e.target.closest('button[data-value]');
      if (!btn) return;
      group.querySelectorAll('button').forEach(function (b) {
        var on = b === btn;
        b.classList.toggle('is-active', on);
        b.setAttribute('aria-pressed', on ? 'true' : 'false');
      });
      filter[group.dataset.filter] = btn.dataset.value;
      shown = PAGE;   // 필터를 바꾸면 처음부터 다시
      render();
    });
  });

  moreBtn.addEventListener('click', function () {
    shown += PAGE;
    render();
  });

  render();
})();
