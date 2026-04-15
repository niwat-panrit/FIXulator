package com.npsoftdev.fixsimulator.plugin;

/**
 * Sidebar navigation sections. Each {@link SimulatorPlugin} declares which
 * section it belongs to; {@link com.npsoftdev.fixsimulator.pages.BasePage}
 * groups plugins by section when rendering the nav.
 */
public enum NavSection {

    /** Top-level items rendered before any section heading (e.g. Dashboard). */
    OVERVIEW(null),

    /** Items grouped under the "FIX Testing" heading. */
    MONITORING("FIX Testing"),

    /** Items grouped under the "Administration" heading. */
    ADMIN("Administration");

    private final String label;

    NavSection(String label) {
        this.label = label;
    }

    /** Section heading shown in the sidebar, or {@code null} for OVERVIEW. */
    public String getLabel() {
        return label;
    }
}
