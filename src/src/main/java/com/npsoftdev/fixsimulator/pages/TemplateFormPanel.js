(function () {
    'use strict';

    // ── Type-input visibility ─────────────────────────────────────────────────

    function updateFieldInputs(selectEl) {
        var row = selectEl.closest('tr');
        if (!row) return;
        row.querySelectorAll('[data-for-type]').forEach(function (d) {
            d.style.display = 'none';
        });
        var target = row.querySelector('[data-for-type="' + selectEl.value + '"]');
        if (target) target.style.display = '';
    }

    function initFieldInputs() {
        document.querySelectorAll('.field-type-select').forEach(function (sel) {
            updateFieldInputs(sel);
        });
    }

    // ── Client-side validation ────────────────────────────────────────────────

    function escapeHtml(str) {
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function clearValidationErrors() {
        document.querySelectorAll('.is-invalid').forEach(function (el) {
            el.classList.remove('is-invalid');
        });
        document.querySelectorAll('.client-invalid-feedback').forEach(function (el) {
            el.remove();
        });
        var summary = document.getElementById('template-validation-summary');
        if (summary) {
            summary.classList.add('d-none');
            summary.innerHTML = '';
        }
    }

    function markInvalid(input, message) {
        input.classList.add('is-invalid');
        // Only insert one feedback element per input
        if (!input.nextSibling || !input.nextSibling.classList ||
                !input.nextSibling.classList.contains('client-invalid-feedback')) {
            var fb = document.createElement('div');
            fb.className = 'invalid-feedback d-block client-invalid-feedback';
            fb.textContent = message;
            input.parentNode.insertBefore(fb, input.nextSibling);
        }
    }

    function validateForm() {
        clearValidationErrors();
        var errors = [];

        // ── Metadata ──────────────────────────────────────────────────────────

        var nameEl = document.querySelector('.tmpl-name');
        if (nameEl && !nameEl.value.trim()) {
            markInvalid(nameEl, 'Template name is required.');
            errors.push('Template name is required.');
        }

        var msgTypeEl = document.querySelector('.tmpl-msgtype');
        if (msgTypeEl && !msgTypeEl.value.trim()) {
            markInvalid(msgTypeEl, 'MsgType (tag 35) is required.');
            errors.push('MsgType (tag 35) is required.');
        }

        var beginStringEl = document.querySelector('.tmpl-beginstring');
        if (beginStringEl && !beginStringEl.value.trim()) {
            markInvalid(beginStringEl, 'BeginString is required (e.g. FIX.4.4).');
            errors.push('BeginString is required.');
        }

        var priorityEl = document.querySelector('.tmpl-priority');
        if (priorityEl) {
            var prio = parseInt(priorityEl.value, 10);
            if (!priorityEl.value.trim() || isNaN(prio) || prio < 1) {
                markInvalid(priorityEl, 'Priority must be a whole number \u2265 1.');
                errors.push('Priority must be a whole number \u2265 1.');
            }
        }

        var scopeTypeEl = document.querySelector('.tmpl-scope-type');
        var scopeSessionIdEl = document.querySelector('.tmpl-scope-session-id');
        if (scopeTypeEl && scopeTypeEl.value === 'Session' &&
                scopeSessionIdEl && !scopeSessionIdEl.value.trim()) {
            markInvalid(scopeSessionIdEl, 'Session ID is required when Scope is \u201cSession\u201d.');
            errors.push('Session ID is required when Scope is \u201cSession\u201d.');
        }

        // ── Field rows ────────────────────────────────────────────────────────

        document.querySelectorAll('.field-row').forEach(function (row, idx) {
            var rowLabel = 'Field row ' + (idx + 1);

            var tagEl = row.querySelector('.field-tag');
            var tagVal = tagEl ? tagEl.value.trim() : '';
            var tag = parseInt(tagVal, 10);

            if (!tagVal || isNaN(tag) || tag < 1) {
                if (tagEl) markInvalid(tagEl, 'Tag must be a positive integer (\u2265 1).');
                errors.push(rowLabel + ': tag must be a positive integer.');
                return; // skip type-specific checks — tag is broken
            }

            var typeEl = row.querySelector('.field-type-select');
            var type = typeEl ? typeEl.value : 'Literal';

            if (type === 'UserInput') {
                var uiNameEl = row.querySelector('.field-ui-name');
                if (uiNameEl && !uiNameEl.value.trim()) {
                    markInvalid(uiNameEl, 'Input name is required — without it the field will be saved as an empty Literal.');
                    errors.push(rowLabel + ' (tag ' + tag + ', UserInput): input name is required.');
                }

            } else if (type === 'Enumeration') {
                var enumNameEl = row.querySelector('.field-enum-name');
                if (enumNameEl && !enumNameEl.value.trim()) {
                    markInvalid(enumNameEl, 'Field name is required for Enumeration.');
                    errors.push(rowLabel + ' (tag ' + tag + ', Enumeration): field name is required.');
                }
                var enumOptsEl = row.querySelector('.field-enum-options');
                if (enumOptsEl && !enumOptsEl.value.trim()) {
                    markInvalid(enumOptsEl, 'At least one option is required for Enumeration.');
                    errors.push(rowLabel + ' (tag ' + tag + ', Enumeration): options are required.');
                }

            } else if (type === 'Derived') {
                var derivedTagEl = row.querySelector('.field-derived-source-tag');
                var derivedTagVal = derivedTagEl ? derivedTagEl.value.trim() : '';
                var srcTag = parseInt(derivedTagVal, 10);
                if (!derivedTagVal || isNaN(srcTag) || srcTag < 1) {
                    if (derivedTagEl) markInvalid(derivedTagEl, 'Source tag must be a positive integer.');
                    errors.push(rowLabel + ' (tag ' + tag + ', Derived): source tag must be a positive integer.');
                }
                var derivedMapEl = row.querySelector('.field-derived-mapping-name');
                if (derivedMapEl && !derivedMapEl.value.trim()) {
                    markInvalid(derivedMapEl, 'Mapping name is required for Derived.');
                    errors.push(rowLabel + ' (tag ' + tag + ', Derived): mapping name is required.');
                }
            }
        });

        // ── Show summary ──────────────────────────────────────────────────────

        if (errors.length > 0) {
            var summary = document.getElementById('template-validation-summary');
            if (summary) {
                var html = '<strong>Please fix the following before saving:</strong>';
                html += '<ul class="mb-0 mt-1 ps-3">';
                errors.forEach(function (e) {
                    html += '<li>' + escapeHtml(e) + '</li>';
                });
                html += '</ul>';
                summary.innerHTML = html;
                summary.classList.remove('d-none');
                summary.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
            return false;
        }
        return true;
    }

    // ── Clear inline error when user corrects a field ─────────────────────────

    function clearFieldError(input) {
        if (!input.classList.contains('is-invalid')) return;
        input.classList.remove('is-invalid');
        var next = input.nextSibling;
        if (next && next.classList && next.classList.contains('client-invalid-feedback')) {
            next.remove();
        }
    }

    // ── Wire up form submit ───────────────────────────────────────────────────

    function hookFormValidation() {
        var form = document.querySelector('form.template-form');
        if (!form) return;
        form.addEventListener('submit', function (e) {
            if (!validateForm()) {
                e.preventDefault();
                e.stopPropagation();
            }
        });
    }

    // ── Expose globally so Wicket AJAX callbacks can call them ────────────────

    window.updateFieldInputs = updateFieldInputs;
    window.initFieldInputs   = initFieldInputs;

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    document.addEventListener('DOMContentLoaded', function () {
        initFieldInputs();
        hookFormValidation();
    });

    // Field-type switcher (event delegation covers AJAX-added rows)
    document.addEventListener('change', function (e) {
        if (e.target && e.target.classList.contains('field-type-select')) {
            updateFieldInputs(e.target);
        }
    });

    // Clear inline error on input/change
    document.addEventListener('input', function (e) {
        if (e.target) clearFieldError(e.target);
    });
    document.addEventListener('change', function (e) {
        if (e.target) clearFieldError(e.target);
    });
}());
