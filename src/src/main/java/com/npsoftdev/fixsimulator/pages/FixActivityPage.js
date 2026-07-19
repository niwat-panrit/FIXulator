(function () {
    'use strict';

    // ── Message detail modal ──────────────────────────────────────────────────
    // Shared FIX utilities (TAG_NAMES, VALUE_DECODERS, escHtml, parseFix) are
    // provided by ComposeMessagePanel.js via the window.FIX namespace.
    document.addEventListener('DOMContentLoaded', function () {

        var modal = document.getElementById('msgDetailModal');
        if (!modal) return;

        modal.addEventListener('show.bs.modal', function (event) {
            var FIX          = window.FIX || {};
            var parseFix     = FIX.parseFix     || function () { return []; };
            var TAG_NAMES    = FIX.TAG_NAMES    || {};
            var VALUE_DECODERS = FIX.VALUE_DECODERS || {};
            var escHtml      = FIX.escHtml      || function (s) { return String(s); };

            var btn = event.relatedTarget;
            if (!btn) return;
            var raw = btn.getAttribute('data-raw') || '';

            document.getElementById('modalRawMsg').value = raw;

            var tbody = document.getElementById('modalFieldsBody');
            tbody.innerHTML = '';

            parseFix(raw).forEach(function (p) {
                var name = TAG_NAMES[p.tag] ? ' (' + TAG_NAMES[p.tag] + ')' : '';
                var dec  = (VALUE_DECODERS[p.tag] || {})[p.value];
                var valDisplay = dec
                    ? escHtml(p.value) + ' <span class="text-muted">(' + escHtml(dec) + ')</span>'
                    : escHtml(p.value);
                var tr = document.createElement('tr');
                tr.innerHTML =
                    '<td class="font-monospace">' + escHtml(p.tag + name) + '</td>' +
                    '<td class="font-monospace">' + valDisplay + '</td>';
                tbody.appendChild(tr);
            });
        });
    });

})();
