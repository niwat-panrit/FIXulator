package com.npsoftdev.fixsimulator.plugins.user.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.core.FixSimulatorSession;
import com.npsoftdev.fixsimulator.plugins.user.api.AuthService;
import com.npsoftdev.fixsimulator.plugins.user.api.RememberMeService;
import com.npsoftdev.fixsimulator.plugins.user.api.User;
import jakarta.servlet.http.Cookie;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.CssUrlReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.PropertyModel;

import java.io.Serializable;
import java.util.Optional;
import com.npsoftdev.fixsimulator.core.ui.HomePage;

/**
 * Login page — does NOT extend {@link BasePage} so it is always accessible
 * regardless of authentication state.
 */
public class LoginPage extends WebPage {

    public LoginPage() {
        // Already signed in → go home
        if (FixSimulatorSession.get().isAuthenticated()) {
            setResponsePage(HomePage.class);
            return;
        }

        LoginModel model = new LoginModel();

        add(new FeedbackPanel("feedback"));

        Form<LoginModel> form = new Form<>("loginForm") {
            @Override
            protected void onSubmit() {
                FixSimulatorApplication app =
                        (FixSimulatorApplication) getApplication();
                AuthService authService = app.getAuthService();
                if (authService == null) {
                    error("Authentication service unavailable. Please try again.");
                    return;
                }

                Optional<User> result = authService.authenticate(model.username, model.password);
                if (result.isEmpty()) {
                    error("Invalid username or password.");
                    return;
                }

                User user = result.get();

                if (!authService.canStartSession(user.username())) {
                    error("Maximum session limit reached for your account. "
                            + "Sign out from another session first.");
                    return;
                }

                FixSimulatorSession session = FixSimulatorSession.get();
                // Bind the session before reading its ID
                session.bind();
                session.signIn(user);
                authService.registerSession(user.username(), session.getId());

                // Restore the FIX session the user had last selected
                app.restoreActiveSession(session, user.username());

                // Persist a remember-me token so the user stays signed in after restart
                RememberMeService rms = app.getRememberMeService();
                if (rms != null) {
                    String token = rms.createToken(user.username());
                    Cookie cookie = new Cookie(FixSimulatorApplication.REMEMBER_ME_COOKIE, token);
                    cookie.setMaxAge(30 * 24 * 3600); // 30 days
                    cookie.setHttpOnly(true);
                    cookie.setPath("/");
                    cookie.setAttribute("SameSite", "Strict");
                    ((WebResponse) getRequestCycle().getResponse()).addCookie(cookie);
                }

                // continueToOriginalDestination() throws RestartResponseException when
                // there is a saved destination; falls through when there is none.
                continueToOriginalDestination();
                setResponsePage(HomePage.class);
            }
        };

        form.add(new TextField<>("username",
                new PropertyModel<>(model, "username")).setRequired(true));
        form.add(new PasswordTextField("password",
                new PropertyModel<>(model, "password")).setRequired(true));

        add(form);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssReferenceHeaderItem.forReference(
                new PackageResourceReference(LoginPage.class, "LoginPage.css")));
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"));
    }

    // ── Form model ────────────────────────────────────────────────────────────

    private static final class LoginModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String username = "";
        String password = "";
    }
}
