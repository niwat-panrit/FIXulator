package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.pages.HomePage;
import org.apache.wicket.Page;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.protocol.http.WebApplication;

public class FixSimulatorApplication extends WebApplication {

    @Override
    public Class<? extends Page> getHomePage() {
        return HomePage.class;
    }

    @Override
    public void init() {
        super.init();
        getMarkupSettings().setDefaultMarkupEncoding("UTF-8");

        // Allow Bootstrap + Bootstrap Icons CDN for stylesheets, scripts and fonts
        getCspSettings().blocking()
                .add(CSPDirective.STYLE_SRC,  CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.STYLE_SRC,  "https://cdn.jsdelivr.net")
                .add(CSPDirective.SCRIPT_SRC, "https://cdn.jsdelivr.net")
                .add(CSPDirective.FONT_SRC,   CSPDirectiveSrcValue.SELF)
                .add(CSPDirective.FONT_SRC,   "https://cdn.jsdelivr.net");
    }
}
