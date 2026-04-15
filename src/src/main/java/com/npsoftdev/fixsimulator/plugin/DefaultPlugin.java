package com.npsoftdev.fixsimulator.plugin;

import com.npsoftdev.fixsimulator.pages.BasePage;

/**
 * Convenience {@link SimulatorPlugin} implementation for built-in pages that
 * need no custom initialisation logic.
 */
public class DefaultPlugin implements SimulatorPlugin {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String label;
    private final String iconClass;
    private final NavSection section;
    private final Class<? extends BasePage> pageClass;

    public DefaultPlugin(String id, String label, String iconClass,
                         NavSection section, Class<? extends BasePage> pageClass) {
        this.id = id;
        this.label = label;
        this.iconClass = iconClass;
        this.section = section;
        this.pageClass = pageClass;
    }

    @Override public String getId()                          { return id; }
    @Override public String getLabel()                       { return label; }
    @Override public String getIconClass()                   { return iconClass; }
    @Override public NavSection getSection()                 { return section; }
    @Override public Class<? extends BasePage> getPageClass(){ return pageClass; }
}
