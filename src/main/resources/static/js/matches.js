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

    var left = hits.length - shown;
    moreBtn.hidden = left <= 0;
    if (left > 0) {
      moreBtn.textContent = '더 보기 (' + left + '경기 남음)';
    }
  }

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
