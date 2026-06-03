(function () {
    'use strict';

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

    // Expose globally so Wicket AJAX callbacks can call initFieldInputs()
    window.updateFieldInputs = updateFieldInputs;
    window.initFieldInputs   = initFieldInputs;

    // Run on initial page load
    document.addEventListener('DOMContentLoaded', initFieldInputs);

    // Event delegation — handles both pre-existing and AJAX-added rows
    document.addEventListener('change', function (e) {
        if (e.target && e.target.classList.contains('field-type-select')) {
            updateFieldInputs(e.target);
        }
    });
}());
