/*
 * 즐겨찾기 + 최근 검색 기록
 *
 * 로그인이 없어서 브라우저(localStorage)에 저장한다. 다른 기기·다른 브라우저에서는 보이지 않는다.
 * 저장 형식: [{ platform: "steam", nickname: "N0-RU", label: "스팀" }, ...]
 *
 * 화면에서 쓰는 표시(data 속성):
 *   data-favorite-toggle  data-platform data-nickname data-label  → ☆ 버튼 (누르면 추가/삭제)
 *   data-remember-search  data-platform data-nickname data-label  → 이 페이지를 최근 검색에 기록
 *   data-player-lists                                             → 즐겨찾기·최근 검색 목록을 그릴 곳 (첫 화면)
 *   data-player-dropdown                                          → 검색창 아래 펼쳐지는 목록 (상단 검색창)
 */
(function () {
  'use strict';

  var FAVORITES = 'pubgg.favorites';
  var RECENT = 'pubgg.recent';
  var MAX_RECENT = 8;

  // ---------- 저장소 (사생활 보호 모드 등에서 막혀도 페이지는 동작하게) ----------

  function load(key) {
    try {
      var value = JSON.parse(localStorage.getItem(key) || '[]');
      return Array.isArray(value) ? value : [];
    } catch (e) {
      return [];
    }
  }

  function save(key, list) {
    try {
      localStorage.setItem(key, JSON.stringify(list));
    } catch (e) {
      // 저장이 막힌 환경이면 조용히 무시
    }
  }

  function same(a, b) {
    return a.platform === b.platform && a.nickname.toLowerCase() === b.nickname.toLowerCase();
  }

  function without(list, player) {
    return list.filter(function (p) { return !same(p, player); });
  }

  function isFavorite(player) {
    return load(FAVORITES).some(function (p) { return same(p, player); });
  }

  function playerFrom(el) {
    return {
      platform: el.getAttribute('data-platform'),
      nickname: el.getAttribute('data-nickname'),
      label: el.getAttribute('data-label')
    };
  }

  function searchUrl(player) {
    return '/search?platform=' + encodeURIComponent(player.platform) +
        '&nickname=' + encodeURIComponent(player.nickname);
  }

  // ---------- ☆ 즐겨찾기 버튼 ----------

  function paintToggle(btn) {
    var on = isFavorite(playerFrom(btn));
    btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    btn.setAttribute('aria-label', on ? '즐겨찾기에서 빼기' : '즐겨찾기에 추가');
    btn.textContent = on ? '★' : '☆';
  }

  document.querySelectorAll('[data-favorite-toggle]').forEach(function (btn) {
    paintToggle(btn);
    btn.addEventListener('click', function () {
      var player = playerFrom(btn);
      var list = without(load(FAVORITES), player);
      if (!isFavorite(player)) {
        list.unshift(player);
      }
      save(FAVORITES, list);
      paintToggle(btn);
    });
  });

  // ---------- 최근 검색에 기록 ----------

  document.querySelectorAll('[data-remember-search]').forEach(function (el) {
    var player = playerFrom(el);
    var list = without(load(RECENT), player);
    list.unshift(player);
    save(RECENT, list.slice(0, MAX_RECENT));
  });

  // ---------- 목록 그리기 ----------

  // 즐겨찾기에서 방금 뺀 것 (잠깐 "되돌리기"를 보여준다)
  var undo = null;
  var undoTimer = null;

  function chip(player, onRemove, starred) {
    var li = document.createElement('li');
    li.className = 'pchip' + (starred ? ' pchip--fav' : '');

    var a = document.createElement('a');
    a.className = 'pchip__link';
    a.href = searchUrl(player);
    var tag = document.createElement('span');
    tag.className = 'pchip__platform';
    tag.textContent = player.label || player.platform;
    a.appendChild(tag);
    var name = document.createElement('span');
    name.className = 'pchip__name';   // 긴 닉네임은 말줄임
    name.textContent = player.nickname;
    a.title = player.nickname;
    a.appendChild(name);
    li.appendChild(a);

    var x = document.createElement('button');
    x.type = 'button';
    x.className = 'pchip__remove';
    x.setAttribute('aria-label', player.nickname + ' 지우기');
    x.textContent = '×';
    // mousedown 에서 막아야 드롭다운이 먼저 닫히지 않는다
    x.addEventListener('mousedown', function (e) { e.preventDefault(); });
    x.addEventListener('click', function (e) {
      e.preventDefault();
      onRemove(player);
    });
    li.appendChild(x);
    return li;
  }

  function section(title, key, emptyText, rerender) {
    var list = load(key);
    var wrap = document.createElement('section');
    wrap.className = 'plist';

    var h = document.createElement('h2');
    h.className = 'plist__title';
    h.textContent = title;
    wrap.appendChild(h);

    if (list.length === 0) {
      var p = document.createElement('p');
      p.className = 'plist__empty';
      p.textContent = emptyText;
      wrap.appendChild(p);
      if (key === FAVORITES) {
        appendUndo(wrap, rerender);
      }
      return wrap;
    }

    var ul = document.createElement('ul');
    ul.className = 'plist__items';
    list.forEach(function (player, index) {
      ul.appendChild(chip(player, function (removed) {
        save(key, without(load(key), removed));
        // 즐겨찾기는 일부러 모은 거라 실수로 지웠을 때 되돌릴 수 있게 (최근 검색은 바로 삭제)
        if (key === FAVORITES) {
          undo = { player: removed, index: index };
          clearTimeout(undoTimer);
          undoTimer = setTimeout(function () { undo = null; rerender(); }, 5000);
        }
        rerender();
      }, key === FAVORITES));
    });
    wrap.appendChild(ul);
    if (key === FAVORITES) {
      appendUndo(wrap, rerender);
    }
    return wrap;
  }

  function appendUndo(wrap, rerender) {
    if (!undo) {
      return;
    }
    var bar = document.createElement('p');
    bar.className = 'plist__undo';
    bar.setAttribute('role', 'status');
    bar.appendChild(document.createTextNode(undo.player.nickname + ' 님을 즐겨찾기에서 뺐어요. '));
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = '되돌리기';
    btn.addEventListener('mousedown', function (e) { e.preventDefault(); });
    btn.addEventListener('click', function () {
      var list = without(load(FAVORITES), undo.player);
      list.splice(Math.min(undo.index, list.length), 0, undo.player);
      save(FAVORITES, list);
      undo = null;
      clearTimeout(undoTimer);
      rerender();
    });
    bar.appendChild(btn);
    wrap.appendChild(bar);
  }

  function render(container) {
    function rerender() { render(container); }
    container.innerHTML = '';
    container.appendChild(section('즐겨찾기', FAVORITES, '검색 결과에서 ☆을 누르면 여기에 모여요.', rerender));
    container.appendChild(section('최근 검색', RECENT, '아직 검색한 플레이어가 없어요.', rerender));
  }

  // 첫 화면: 항상 보이는 목록
  document.querySelectorAll('[data-player-lists]').forEach(render);

  // 상단 검색창: 입력창을 누르면 펼쳐지는 목록
  document.querySelectorAll('[data-player-dropdown]').forEach(function (panel) {
    var input = document.getElementById(panel.getAttribute('data-for'));
    if (!input) {
      return;
    }
    function open() {
      if (load(FAVORITES).length === 0 && load(RECENT).length === 0) {
        return;   // 보여줄 게 없으면 열지 않음
      }
      render(panel);
      panel.hidden = false;
    }
    function close() {
      panel.hidden = true;
    }
    // 검색창에 지금 닉네임이 채워져 있으면 목록은 열지 않고, 비어 있을 때만 연다
    input.addEventListener('focus', function () {
      if (!input.value) { open(); }
    });
    input.addEventListener('input', function () {
      if (input.value) { close(); } else { open(); }
    });
    input.addEventListener('blur', function () {
      // 목록 안의 링크를 누를 시간을 준다
      setTimeout(function () {
        if (!panel.contains(document.activeElement)) { close(); }
      }, 150);
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { close(); }
    });
  });
})();
