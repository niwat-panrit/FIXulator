package com.npsoftdev.fixsimulator.pages;

import com.npsoftdev.fixsimulator.FixSimulatorApplication;
import com.npsoftdev.fixsimulator.FixSimulatorSession;
import com.npsoftdev.fixsimulator.user.AuthService;
import com.npsoftdev.fixsimulator.user.User;
import org.apache.wicket.markup.head.CssUrlReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptUrlReferenceHeaderItem;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.PropertyModel;

import java.io.Serializable;
import java.util.Optional;

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
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"));
        response.render(CssUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"));
        response.render(JavaScriptUrlReferenceHeaderItem.forUrl(
                "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"));
    }

    // ── Form model ────────────────────────────────────────────────────────────

    private static final class LoginModel implements Serializable {
        private static final long serialVersionUID = 1L;
        String username = "";
        String password = "";
    }
}
