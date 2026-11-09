(function () {
  // 仅在 iframe 预览（编辑器内）启用；导出的独立站点中该脚本不产生任何行为
  if (window.top === window.self) return;

  var selectedEl = null;
  var seq = 0;

  // 拖动状态
  var toolbar = null;
  var dragging = false;
  var dragAnchor = null;
  var dragDx = 0;
  var dragDy = 0;
  var baseX = 0;
  var baseY = 0;
  var lastOriginalStyle = null;

  var style = document.createElement('style');
  style.setAttribute('data-hb-bridge-style', '1');
  style.textContent = '.hb-edit-hover{outline:2px dashed #6366f1 !important;outline-offset:2px !important;cursor:crosshair !important}' +
    '.hb-edit-selected{outline:2px solid #6366f1 !important;outline-offset:2px !important;box-shadow:0 0 0 4px rgba(99,102,241,.18) !important}' +
    '.hb-drag-target{outline:2px solid #f59e0b !important;cursor:move !important}' +
    '.hb-toolbar{position:fixed;z-index:2147483647;display:none;align-items:center;gap:2px;padding:4px;border-radius:9px;background:#111827;box-shadow:0 8px 22px rgba(15,23,42,.35);font:12px/1.2 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;user-select:none}' +
    '.hb-toolbar button{border:0;background:transparent;color:#e5e7eb;padding:5px 9px;border-radius:6px;cursor:pointer;font-size:12px;white-space:nowrap}' +
    '.hb-toolbar button:hover{background:rgba(255,255,255,.14)}' +
    '.hb-toolbar.hb-dragging button[data-act="move"]{background:#6366f1;color:#fff}' +
    '.hb-toolbar span{color:#94a3b8;padding:0 6px;font-size:11px;max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}';
  (document.head || document.documentElement).appendChild(style);

  function cssPath(el) {
    var parts = [];
    while (el && el.nodeType === 1 && el !== document.documentElement && el !== document.body) {
      var tag = el.tagName.toLowerCase();
      if (el.id && /^[A-Za-z][\w:-]*$/.test(el.id)) {
        parts.unshift('#' + el.id);
        break;
      }
      var parent = el.parentNode;
      if (!parent) break;
      var same = Array.prototype.filter.call(parent.children || [], function (c) { return c.tagName === el.tagName; });
      var idx = same.indexOf(el) + 1;
      parts.unshift(tag + (same.length > 1 ? ':nth-of-type(' + idx + ')' : ''));
      el = parent;
    }
    return parts.length ? parts.join(' > ') : 'body';
  }

  function buildInfo(el) {
    if (!el.dataset.hbId) {
      el.dataset.hbId = 'hb-' + (++seq) + '-' + Date.now().toString(36);
    }
    return {
      type: 'hb:select',
      hbId: el.dataset.hbId,
      selector: cssPath(el),
      tag: el.tagName.toLowerCase(),
      text: (el.innerText || '').trim().replace(/\s+/g, ' ').slice(0, 200),
      outerHTML: el.outerHTML.slice(0, 12000),
      style: el.getAttribute('style') || '',
      page: location.pathname
    };
  }

  // ===== 选中浮层（移动 / 撤回）=====
  function ensureToolbar() {
    if (toolbar) return toolbar;
    toolbar = document.createElement('div');
    toolbar.className = 'hb-toolbar';
    toolbar.setAttribute('data-hb-toolbar', '1');
    toolbar.innerHTML = '<span class="hb-tb-tag"></span>' +
      '<button type="button" data-act="move">✥ 移动</button>' +
      '<button type="button" data-act="undo">↩ 撤回</button>';
    toolbar.addEventListener('mousedown', function (e) { e.stopPropagation(); }, true);
    toolbar.addEventListener('click', function (e) {
      var t = e.target;
      if (!t || t.tagName !== 'BUTTON') return;
      e.preventDefault();
      e.stopPropagation();
      if (t.getAttribute('data-act') === 'move') {
        if (dragging) {
          dropDrag();
        } else {
          startDrag();
        }
      } else if (t.getAttribute('data-act') === 'undo') {
        revertPosition();
      }
    }, true);
    (document.body || document.documentElement).appendChild(toolbar);
    return toolbar;
  }

  function positionToolbar() {
    if (!toolbar || !selectedEl) return;
    var r = selectedEl.getBoundingClientRect();
    var top = r.top - 38;
    if (top < 4) top = Math.min(window.innerHeight - 40, r.bottom + 8);
    var left = Math.max(4, Math.min(r.left, window.innerWidth - 210));
    toolbar.style.top = top + 'px';
    toolbar.style.left = left + 'px';
  }

  function showToolbar() {
    ensureToolbar();
    var tagEl = toolbar.querySelector('.hb-tb-tag');
    if (tagEl && selectedEl) tagEl.textContent = '<' + selectedEl.tagName.toLowerCase() + '>';
    toolbar.style.display = 'flex';
    toolbar.classList.toggle('hb-dragging', dragging);
    positionToolbar();
  }

  function hideToolbar() {
    if (toolbar) toolbar.style.display = 'none';
    dragging = false;
    dragAnchor = null;
    lastOriginalStyle = null;
    if (toolbar) toolbar.classList.remove('hb-dragging');
    document.body.style.userSelect = '';
  }

  // 读取元素已有的相对位移（translate: Xpx Ypx），在它的基础上累加拖动距离
  function readTranslate(el) {
    var m = /translate\s*:\s*(-?[\d.]+)px\s+(-?[\d.]+)px/.exec(el.getAttribute('style') || '');
    if (m) return { x: parseFloat(m[1]), y: parseFloat(m[2]) };
    return { x: 0, y: 0 };
  }

  function startDrag() {
    if (!selectedEl) return;
    lastOriginalStyle = selectedEl.getAttribute('style') || '';
    var base = readTranslate(selectedEl);
    baseX = base.x;
    baseY = base.y;
    dragAnchor = null;
    dragDx = 0;
    dragDy = 0;
    dragging = true;
    selectedEl.classList.add('hb-drag-target');
    document.body.style.userSelect = 'none';
    if (toolbar) toolbar.classList.add('hb-dragging');
  }

  function dropDrag() {
    if (!selectedEl || !dragging) return;
    dragging = false;
    document.body.style.userSelect = '';
    if (toolbar) toolbar.classList.remove('hb-dragging');
    var el = selectedEl;
    el.classList.remove('hb-drag-target');
    positionToolbar();
    // 只是点击「移动」按钮而没有真正拖动时，不产生修改
    if (!dragDx && !dragDy) return;
    try {
      parent.postMessage({
        type: 'hb:position',
        selector: cssPath(el),
        tag: el.tagName.toLowerCase(),
        style: el.getAttribute('style') || '',
        page: location.pathname
      }, '*');
    } catch (err) {}
  }

  function revertPosition() {
    if (!selectedEl) return;
    if (dragging) {
      dragging = false;
      dragAnchor = null;
      dragDx = 0;
      dragDy = 0;
      selectedEl.style.translate = '';
      selectedEl.classList.remove('hb-drag-target');
      document.body.style.userSelect = '';
      if (toolbar) toolbar.classList.remove('hb-dragging');
    }
    if (lastOriginalStyle === null) return;
    selectedEl.setAttribute('style', lastOriginalStyle);
    var styleText = selectedEl.getAttribute('style') || '';
    lastOriginalStyle = null;
    positionToolbar();
    try {
      parent.postMessage({
        type: 'hb:position-revert',
        selector: cssPath(selectedEl),
        tag: selectedEl.tagName.toLowerCase(),
        style: styleText,
        page: location.pathname
      }, '*');
    } catch (err) {}
  }

  function clearHighlight() {
    if (selectedEl) {
      selectedEl.classList.remove('hb-edit-selected');
      selectedEl.classList.remove('hb-drag-target');
    }
    Array.prototype.forEach.call(document.querySelectorAll('.hb-edit-hover'), function (n) {
      n.classList.remove('hb-edit-hover');
    });
  }

  document.addEventListener('mouseover', function (e) {
    if (dragging) return;
    var t = e.target;
    if (!t || t.nodeType !== 1 || t === document.documentElement || t === document.body || t === selectedEl) return;
    if (toolbar && (t === toolbar || toolbar.contains(t))) return;
    Array.prototype.forEach.call(document.querySelectorAll('.hb-edit-hover'), function (n) {
      n.classList.remove('hb-edit-hover');
    });
    t.classList.add('hb-edit-hover');
  }, true);

  document.addEventListener('mouseout', function (e) {
    if (e.target && e.target.nodeType === 1) e.target.classList.remove('hb-edit-hover');
  }, true);

  // 拖动模式下元素跟随鼠标移动，点击落下
  document.addEventListener('mousemove', function (e) {
    if (!dragging || !selectedEl) return;
    if (!dragAnchor) dragAnchor = { x: e.clientX, y: e.clientY };
    dragDx = e.clientX - dragAnchor.x;
    dragDy = e.clientY - dragAnchor.y;
    // 用 CSS translate 单独做位移：不脱离文档流、不触发兄弟元素重排，
    // 也不占用 transform，避免覆盖元素自身的动效
    selectedEl.style.translate = (baseX + dragDx) + 'px ' + (baseY + dragDy) + 'px';
    positionToolbar();
    e.preventDefault();
  }, true);

  document.addEventListener('click', function (e) {
    var t = e.target;
    if (!t || t.nodeType !== 1) return;
    if (toolbar && (t === toolbar || toolbar.contains(t))) return;
    if (dragging) {
      e.preventDefault();
      e.stopPropagation();
      dropDrag();
      return;
    }
    if (t === document.documentElement || t === document.body) return;
    e.preventDefault();
    e.stopPropagation();
    clearHighlight();
    selectedEl = t;
    lastOriginalStyle = null;
    t.classList.add('hb-edit-selected');
    showToolbar();
    try { parent.postMessage(buildInfo(t), '*'); } catch (err) {}
  }, true);

  document.addEventListener('submit', function (e) {
    e.preventDefault();
    e.stopPropagation();
  }, true);

  window.addEventListener('resize', function () {
    if (selectedEl && toolbar && toolbar.style.display !== 'none') positionToolbar();
  });

  document.addEventListener('scroll', function () {
    if (selectedEl && toolbar && toolbar.style.display !== 'none') positionToolbar();
  }, true);

  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || typeof d !== 'object') return;
    if (d.type === 'hb:clear') {
      clearHighlight();
      hideToolbar();
      selectedEl = null;
    }
  });

  try { parent.postMessage({ type: 'hb:ready', page: location.pathname }, '*'); } catch (err) {}
})();