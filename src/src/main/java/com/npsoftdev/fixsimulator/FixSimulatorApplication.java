package com.npsoftdev.fixsimulator;

import com.npsoftdev.fixsimulator.pages.HomePage;
import org.apache.wicket.Page;
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
    }
}
