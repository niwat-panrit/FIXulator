(function () {
    'use strict';

    // ── FIX tag name dictionary ───────────────────────────────────────────────
    var TAG_NAMES = {
        '1':   'Account',
        '6':   'AvgPx',
        '8':   'BeginString',
        '9':   'BodyLength',
        '10':  'CheckSum',
        '11':  'ClOrdID',
        '14':  'CumQty',
        '17':  'ExecID',
        '18':  'ExecInst',
        '21':  'HandlInst',
        '22':  'IDSource',
        '31':  'LastPx',
        '32':  'LastQty',
        '34':  'MsgSeqNum',
        '35':  'MsgType',
        '37':  'OrderID',
        '38':  'OrderQty',
        '39':  'OrdStatus',
        '40':  'OrdType',
        '41':  'OrigClOrdID',
        '44':  'Price',
        '48':  'SecurityID',
        '49':  'SenderCompID',
        '50':  'SenderSubID',
        '52':  'SendingTime',
        '54':  'Side',
        '55':  'Symbol',
        '56':  'TargetCompID',
        '57':  'TargetSubID',
        '58':  'Text',
        '59':  'TimeInForce',
        '60':  'TransactTime',
        '102': 'CxlRejReason',
        '108': 'HeartBtInt',
        '112': 'TestReqID',
        '150': 'ExecType',
        '151': 'LeavesQty',
        '371': 'RefTagID',
        '372': 'RefMsgType',
        '373': 'SessionRejectReason'
    };

    // ── Human-readable value decoders for common enums ────────────────────────
    var VALUE_DECODERS = {
        '35': {
            '0': 'Heartbeat', '1': 'TestRequest', '2': 'ResendRequest',
            '3': 'Reject', '4': 'SequenceReset', '5': 'Logout',
            '8': 'ExecutionReport', '9': 'OrderCancelReject', 'A': 'Logon',
            'D': 'NewOrderSingle', 'F': 'OrderCancelRequest',
            'G': 'OrderCancelReplaceRequest'
        },
        '39': {
            '0': 'New', '1': 'PartiallyFilled', '2': 'Filled',
            '3': 'DoneForDay', '4': 'Cancelled', '5': 'Replaced',
            '6': 'PendingCancel', '7': 'Stopped', '8': 'Rejected',
            '9': 'Suspended', 'A': 'PendingNew', 'E': 'PendingReplace'
        },
        '40': {
            '1': 'Market', '2': 'Limit', '3': 'Stop', '4': 'StopLimit',
            '5': 'MarketOnClose', '6': 'WithOrWithout', '7': 'LimitOrBetter'
        },
        '54': { '1': 'Buy', '2': 'Sell', '3': 'BuyMinus', '4': 'SellPlus', '5': 'SellShort' },
        '59': {
            '0': 'Day', '1': 'GoodTillCancel', '2': 'AtTheOpening',
            '3': 'ImmediateOrCancel', '4': 'FillOrKill', '6': 'GoodTillDate'
        },
        '150': {
            '0': 'New', '1': 'PartialFill', '2': 'Fill', '3': 'DoneForDay',
            '4': 'Cancelled', '5': 'Replaced', '6': 'PendingCancel',
            '7': 'Stopped', '8': 'Rejected', 'C': 'Expired', 'F': 'Trade'
        }
    };

    // ── Escape HTML special characters ────────────────────────────────────────
    function escHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // ── Parse a delimited FIX string into an array of {tag, value} pairs ──────
    // delimiter defaults to '|'; auto-detected as SOH (\x01) when present in raw
    function parseFix(raw, delimiter) {
        delimiter = delimiter || '|';
        var pairs = [];
        // Use indexOf+slice loop to avoid any regex edge-cases with special delimiters
        var start = 0;
        while (start <= raw.length) {
            var end = raw.indexOf(delimiter, start);
            if (end === -1) end = raw.length;
            var chunk = raw.substring(start, end);
            var eq = chunk.indexOf('=');
            if (eq >= 1) {
                pairs.push({ tag: chunk.substring(0, eq).trim(), value: chunk.substring(eq + 1) });
            }
            start = end + delimiter.length;
        }
        return pairs;
    }

    // Engine-owned tags that QuickFIX/J always sets from session config
    var SESSION_TAGS = { '8': true, '9': true, '10': true, '34': true, '49': true, '52': true, '56': true };

    // ── Compose offcanvas: parse raw FIX into fields preview ─────────────────
    function parseComposeMsg() {
        var raw     = (document.getElementById('composeTextarea') || {}).value || '';
        var delim   = ((document.getElementById('composeDelimiter') || {}).value || '').trim() || '|';
        var tbody   = document.getElementById('composeFieldsBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        // Auto-detect real SOH messages
        if (raw.indexOf('\x01') !== -1) delim = '\x01';

        // Read active session info for field replacement display
        var holder    = document.getElementById('activeSessionInfo') || {};
        var sesBegin  = holder.getAttribute ? (holder.getAttribute('data-begin-string') || '') : '';
        var sesSender = holder.getAttribute ? (holder.getAttribute('data-sender') || '') : '';
        var sesTarget = holder.getAttribute ? (holder.getAttribute('data-target') || '') : '';
        var sessionValues = { '8': sesBegin, '49': sesSender, '56': sesTarget,
                              '9': '', '34': '(auto)', '52': '(auto)', '10': '(auto)' };

        var pairs = parseFix(raw, delim);
        if (pairs.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="2" class="text-muted text-center py-2">' +
                'No parseable fields found</td></tr>';
            return;
        }

        pairs.forEach(function (p) {
            var name = TAG_NAMES[p.tag] ? ' (' + TAG_NAMES[p.tag] + ')' : '';
            var isSessionTag = SESSION_TAGS[p.tag];
            var displayValue, note;

            if (isSessionTag && sessionValues[p.tag]) {
                displayValue = escHtml(sessionValues[p.tag]);
                note = ' <span class="text-muted" style="font-size:0.72rem;">(from active session)</span>';
            } else if (isSessionTag) {
                displayValue = escHtml(p.value);
                note = ' <span class="text-muted" style="font-size:0.72rem;">(set by engine)</span>';
            } else {
                var dec = (VALUE_DECODERS[p.tag] || {})[p.value];
                displayValue = dec
                    ? escHtml(p.value) + ' <span class="text-muted">(' + escHtml(dec) + ')</span>'
                    : escHtml(p.value);
                note = '';
            }

            var rowCls = isSessionTag ? ' class="text-muted"' : '';
            var tr = document.createElement('tr');
            tr.innerHTML =
                '<td class="font-monospace"' + rowCls + '>' + escHtml(p.tag + name) + '</td>' +
                '<td class="font-monospace"' + rowCls + '>' + displayValue + note + '</td>';
            tbody.appendChild(tr);
        });

        // Append engine-auto fields that weren't in the message
        [{ tag: '34', label: 'MsgSeqNum' }, { tag: '52', label: 'SendingTime' }, { tag: '10', label: 'CheckSum' }]
            .forEach(function (e) {
                if (pairs.some(function (p) { return p.tag === e.tag; })) return;
                var tr = document.createElement('tr');
                tr.innerHTML =
                    '<td class="font-monospace text-muted">' + e.tag + ' (' + e.label + ')</td>' +
                    '<td class="font-monospace text-muted" style="font-size:0.72rem;">(set by engine)</td>';
                tbody.appendChild(tr);
            });
    }

    // ── Wire up all event listeners after DOM is ready ────────────────────────
    document.addEventListener('DOMContentLoaded', function () {

        // Parse button — avoids inline onclick (blocked by CSP)
        var parseBtn = document.getElementById('composeParseBtnId');
        if (parseBtn) parseBtn.addEventListener('click', parseComposeMsg);

        // Message detail modal
        var modal = document.getElementById('msgDetailModal');
        if (!modal) return;

        modal.addEventListener('show.bs.modal', function (event) {
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
