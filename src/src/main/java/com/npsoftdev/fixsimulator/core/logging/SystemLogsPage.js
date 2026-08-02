/**
 * SystemLogs — client-side log viewer
 *
 * Features:
 *  - Live tail via Wicket AJAX polling (2 s interval)
 *  - Line wrap toggle
 *  - Pause / resume tailing
 *  - Search with plain-string or regex, with match highlighting
 */
var SystemLogs = (function () {
    'use strict';

    var callbackUrl = '';
    var nextOffset  = 0;
    var tailing     = true;
    var pollTimer   = null;
    var searchTimer = null;

    /* ── Public API ─────────────────────────────────────────────────────────── */

    function init(opts) {
        callbackUrl = opts.tailUrl   || '';
        nextOffset  = opts.nextOffset || 0;

        if (opts.lines && opts.lines.length > 0) {
            _appendLines(opts.lines);
        }

        _bindControls();
        _updateStatus();
        _scrollToBottom();

        if (callbackUrl) {
            pollTimer = setInterval(_poll, 2000);
        }

        // Clean up the timer when the page is unloaded
        window.addEventListener('beforeunload', function () {
            if (pollTimer) clearInterval(pollTimer);
        });
    }

    /** Called by Wicket's appendJavaScript when the tail behavior responds. */
    function onData(data) {
        if (data.nextOffset !== undefined) nextOffset = data.nextOffset;
        if (data.lines && data.lines.length > 0) {
            _appendLines(data.lines);
        }
    }

    /* ── Private helpers ─────────────────────────────────────────────────────── */

    function _bindControls() {
        var wrapEl   = document.getElementById('wrapToggle');
        var tailEl   = document.getElementById('tailToggle');
        var searchEl = document.getElementById('searchInput');
        var regexEl  = document.getElementById('regexToggle');
        var clearEl  = document.getElementById('searchClear');

        wrapEl.addEventListener('change', function (e) {
            document.getElementById('logContent')
                    .classList.toggle('log-wrapped', e.target.checked);
        });

        tailEl.addEventListener('change', function (e) {
            tailing = e.target.checked;
            _setTailIndicator(tailing);
            if (tailing) {
                _scrollToBottom();
                if (!pollTimer && callbackUrl) {
                    pollTimer = setInterval(_poll, 2000);
                }
            }
        });

        searchEl.addEventListener('input', function () {
            clearTimeout(searchTimer);
            searchTimer = setTimeout(_applySearch, 250);
        });

        regexEl.addEventListener('change', _applySearch);

        clearEl.addEventListener('click', function () {
            document.getElementById('searchInput').value = '';
            _applySearch();
        });
    }

    function _poll() {
        if (!tailing) return;
        Wicket.Ajax.get({ u: callbackUrl + '&since=' + nextOffset });
    }

    function _appendLines(lines) {
        var container = document.getElementById('logLines');
        var frag      = document.createDocumentFragment();
        var term      = document.getElementById('searchInput').value;
        var isRegex   = document.getElementById('regexToggle').checked;
        var regex     = term ? _buildRegex(term, isRegex) : null;

        for (var i = 0; i < lines.length; i++) {
            var text = lines[i];
            var div  = document.createElement('div');
            div.className       = 'log-line ' + _levelClass(text);
            div.dataset.text    = text;

            if (regex) {
                regex.lastIndex = 0;
                if (regex.test(text)) {
                    regex.lastIndex = 0;
                    div.innerHTML = _highlightText(text, regex);
                } else {
                    div.style.display = 'none';
                    div.textContent   = text;
                }
            } else {
                div.textContent = text;
            }

            frag.appendChild(div);
        }

        container.appendChild(frag);
        _updateStatus();
        if (tailing) _scrollToBottom();
    }

    function _applySearch() {
        var term    = document.getElementById('searchInput').value;
        var isRegex = document.getElementById('regexToggle').checked;
        var allLines = document.querySelectorAll('#logLines .log-line');

        if (!term) {
            allLines.forEach(function (el) {
                el.style.display = '';
                el.textContent   = el.dataset.text || el.textContent;
            });
            _updateStatus();
            return;
        }

        var regex = _buildRegex(term, isRegex);

        allLines.forEach(function (el) {
            var text = el.dataset.text || el.textContent;
            regex.lastIndex = 0;
            if (regex.test(text)) {
                el.style.display = '';
                regex.lastIndex  = 0;
                el.innerHTML     = _highlightText(text, regex);
            } else {
                el.style.display = 'none';
            }
        });

        _updateStatus();
    }

    function _buildRegex(term, isRegex) {
        try {
            return isRegex
                ? new RegExp(term, 'gi')
                : new RegExp(_escapeRegex(term), 'gi');
        } catch (e) {
            return new RegExp(_escapeRegex(term), 'gi');
        }
    }

    function _highlightText(text, regex) {
        regex.lastIndex = 0;
        var result    = '';
        var lastIndex = 0;
        var match;

        while ((match = regex.exec(text)) !== null) {
            // Guard against zero-length match infinite loops
            if (match.index === lastIndex && match[0].length === 0) {
                lastIndex++;
                continue;
            }
            result    += _escHtml(text.slice(lastIndex, match.index));
            result    += '<mark>' + _escHtml(match[0]) + '</mark>';
            lastIndex  = match.index + match[0].length;
        }
        result += _escHtml(text.slice(lastIndex));
        return result;
    }

    function _levelClass(text) {
        if (/\bERROR\b/.test(text)) return 'log-error';
        if (/\bWARN\b/.test(text))  return 'log-warn';
        if (/\bDEBUG\b/.test(text)) return 'log-debug';
        return '';
    }

    function _scrollToBottom() {
        var el = document.getElementById('logContent');
        if (el) el.scrollTop = el.scrollHeight;
    }

    function _setTailIndicator(active) {
        var dot   = document.getElementById('tailDot');
        var label = document.getElementById('tailLabel');
        if (dot)   dot.classList.toggle('log-tail-active', active);
        if (label) label.textContent = active ? 'Tailing' : 'Paused';
    }

    function _updateStatus() {
        var allLines  = document.querySelectorAll('#logLines .log-line');
        var visible   = document.querySelectorAll('#logLines .log-line:not([style*="display: none"]):not([style*="display:none"])');
        var total     = allLines.length;
        var term      = document.getElementById('searchInput').value;
        var statusEl  = document.getElementById('logStatus');
        var searchEl  = document.getElementById('searchStatus');

        statusEl.textContent = total + ' line' + (total !== 1 ? 's' : '');

        if (term) {
            var m = visible.length;
            searchEl.style.display = '';
            searchEl.textContent   = ' — ' + m + ' match' + (m !== 1 ? 'es' : '');
        } else {
            searchEl.style.display = 'none';
            searchEl.textContent   = '';
        }

        _setTailIndicator(tailing);
    }

    function _escHtml(s) {
        return s.replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
    }

    function _escapeRegex(s) {
        return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    /* ── Public surface ──────────────────────────────────────────────────────── */

    return { init: init, onData: onData };

}());
