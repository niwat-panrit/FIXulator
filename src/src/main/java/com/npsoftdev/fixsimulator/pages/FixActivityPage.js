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

    // ── Parse a pipe-delimited FIX string into an array of {tag, value} pairs ─
    function parseFix(raw) {
        var pairs = [];
        raw.split('|').forEach(function (chunk) {
            var eq = chunk.indexOf('=');
            if (eq < 1) return;
            pairs.push({ tag: chunk.substring(0, eq).trim(), value: chunk.substring(eq + 1) });
        });
        return pairs;
    }

    // ── Populate the message detail modal ─────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        var modal = document.getElementById('msgDetailModal');
        if (!modal) return;

        modal.addEventListener('show.bs.modal', function (event) {
            var btn = event.relatedTarget;
            if (!btn) return;
            var raw = btn.getAttribute('data-raw') || '';

            // Populate raw message textarea
            document.getElementById('modalRawMsg').value = raw;

            // Populate parsed fields table
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

    // ── Compose offcanvas: parse raw FIX into fields preview ─────────────────
    window.parseComposeMsg = function () {
        var raw = (document.getElementById('composeTextarea') || {}).value || '';
        var tbody = document.getElementById('composeFieldsBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        var pairs = parseFix(raw);
        if (pairs.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="2" class="text-muted text-center py-2">' +
                'No parseable fields found</td></tr>';
            return;
        }

        pairs.forEach(function (p) {
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
    };

})();
