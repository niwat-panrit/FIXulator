package com.npsoftdev.fixsimulator.plugins.user.ui;

import com.npsoftdev.fixsimulator.core.FixSimulatorSession;
import com.npsoftdev.fixsimulator.plugins.user.internal.DefaultAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.npsoftdev.fixsimulator.plugins.user.api.RoleRegistry;
import com.npsoftdev.fixsimulator.plugins.user.api.User;
import com.npsoftdev.fixsimulator.plugins.user.api.UserRepository;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.npsoftdev.fixsimulator.core.ui.BasePage;

public class UserManagementPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(UserManagementPage.class);

    /** Curated list of IANA timezone IDs shown in the timezone picker. */
    private static final List<String> TIMEZONE_CHOICES = List.of(
            "UTC",
            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
            "America/Sao_Paulo",
            "Europe/London", "Europe/Amsterdam", "Europe/Berlin", "Europe/Paris",
            "Europe/Zurich", "Europe/Stockholm", "Europe/Moscow",
            "Africa/Johannesburg",
            "Asia/Dubai", "Asia/Kolkata", "Asia/Bangkok", "Asia/Jakarta",
            "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Singapore",
            "Asia/Tokyo", "Asia/Seoul",
            "Australia/Sydney", "Pacific/Auckland"
    );

    private final UserFormModel formModel = new UserFormModel();

    // Keep a direct reference so the ListView closures can add them to AJAX targets
    private final WebMarkupContainer listContainer;
    private final Form<UserFormModel> userForm;
    private final FeedbackPanel pageFeedback;

    public UserManagementPage() {
        super();

        pageFeedback = new FeedbackPanel("feedback");
        pageFeedback.setOutputMarkupId(true);
        add(pageFeedback);

        // ── User list ─────────────────────────────────────────────────────────
        listContainer = new WebMarkupContainer("listContainer");
        listContainer.setOutputMarkupId(true);
        add(listContainer);

        listContainer.add(new ListView<User>("userList",
                new LoadableDetachableModel<>() {
                    @Override
                    protected List<User> load() {
                        UserRepository repo = app().getUserRepository();
                        return repo != null ? repo.findAll() : List.of();
                    }
                }) {
            @Override
            protected void populateItem(ListItem<User> item) {
                User u = item.getModelObject();
                item.add(new Label("username",    u.username()));
                item.add(new Label("displayName", u.displayName() != null ? u.displayName() : ""));
                item.add(new Label("email",       u.email() != null ? u.email() : ""));
                item.add(new Label("roles",       u.roles().isEmpty()
                        ? "—" : String.join(", ", u.roles())));
                item.add(new Label("status",      u.isActive() ? "Active" : "Inactive"));
                item.add(new Label("maxSessions", u.maxSessions() == 0
                        ? "Unlimited" : String.valueOf(u.maxSessions())));

                // Edit
                item.add(new AjaxLink<Void>("editBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        formModel.loadFrom(u);
                        target.add(userForm);
                        target.appendJavaScript(
                                "new bootstrap.Modal(document.getElementById('userModal')).show();");
                    }
                });

                // Delete
                item.add(new AjaxLink<Void>("deleteBtn") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        String err = validateDelete(u.username());
                        if (err != null) {
                            error(err);
                            target.add(pageFeedback);
                            return;
                        }
                        app().getUserRepository().delete(u.username());
                        log.info("User '{}' deleted by '{}'", u.username(),
                                FixSimulatorSession.get().getAuthenticatedUser() != null
                                        ? FixSimulatorSession.get().getAuthenticatedUser().username() : "?");
                        target.add(listContainer, pageFeedback);
                    }
                });
            }
        });

        // ── Add User button ───────────────────────────────────────────────────
        add(new AjaxLink<Void>("addUserBtn") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                formModel.reset();
                target.add(userForm);
                target.appendJavaScript(
                        "new bootstrap.Modal(document.getElementById('userModal')).show();");
            }
        });

        // ── Add / Edit form (in Bootstrap modal) ──────────────────────────────
        userForm = new Form<>("userForm", new CompoundPropertyModel<>(formModel));
        userForm.setOutputMarkupId(true);
        add(userForm);

        FeedbackPanel formFeedback = new FeedbackPanel("formFeedback");
        formFeedback.setOutputMarkupId(true);
        userForm.add(formFeedback);

        // Modal title
        userForm.add(new Label("modalTitle", (IModel<String>) () ->
                formModel.editingUsername == null ? "Add User" : "Edit User"));

        // Username — editable for new, read-only label for edit
        WebMarkupContainer usernameEditRow = new WebMarkupContainer("usernameEditRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(formModel.editingUsername == null);
            }
        };
        TextField<String> usernameField = new TextField<>("username",
                new PropertyModel<>(formModel, "username"));
        usernameField.setRequired(true);
        usernameEditRow.add(usernameField);
        userForm.add(usernameEditRow);

        WebMarkupContainer usernameReadonlyRow = new WebMarkupContainer("usernameReadonlyRow") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(formModel.editingUsername != null);
            }
        };
        usernameReadonlyRow.add(new Label("usernameReadonly",
                (IModel<String>) () -> formModel.username != null ? formModel.username : ""));
        userForm.add(usernameReadonlyRow);

        // Display name & email
        userForm.add(new TextField<>("displayName",
                new PropertyModel<>(formModel, "displayName")));
        userForm.add(new TextField<>("email",
                new PropertyModel<>(formModel, "email")));

        // Password (required for new, optional for edit)
        PasswordTextField passwordField = new PasswordTextField("password",
                new PropertyModel<>(formModel, "password"));
        passwordField.setRequired(false);
        userForm.add(passwordField);

        PasswordTextField confirmField = new PasswordTextField("confirmPassword",
                new PropertyModel<>(formModel, "confirmPassword"));
        confirmField.setRequired(false);
        userForm.add(confirmField);

        // Roles (checkboxes)
        userForm.add(new CheckBox("roleAdmin",  new PropertyModel<>(formModel, "roleAdmin")));
        userForm.add(new CheckBox("roleTester", new PropertyModel<>(formModel, "roleTester")));

        // Active & session limit
        userForm.add(new CheckBox("active", new PropertyModel<>(formModel, "active")));
        userForm.add(new NumberTextField<>("maxSessions",
                new PropertyModel<>(formModel, "maxSessions"), Integer.class).setMinimum(0));

        // Timezone
        DropDownChoice<String> tzDd = new DropDownChoice<>("timezone",
                new PropertyModel<>(formModel, "timezone"),
                TIMEZONE_CHOICES,
                new IChoiceRenderer<>() {
                    @Override public Object getDisplayValue(String id) { return tzLabel(id); }
                    @Override public String getIdValue(String id, int i) { return id; }
                    @Override public String getObject(String id, IModel<? extends List<? extends String>> c) { return id; }
                });
        tzDd.setNullValid(true);
        userForm.add(tzDd);

        // Save button
        userForm.add(new AjaxButton("saveBtn", userForm) {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                String err = validateForm();
                if (err != null) {
                    userForm.error(err);
                    target.add(userForm);
                    return;
                }

                UserRepository repo = app().getUserRepository();
                boolean isNew = (formModel.editingUsername == null);
                List<String> roles = formModel.collectRoles();

                if (isNew) {
                    if (repo.findByUsername(formModel.username.trim()).isPresent()) {
                        userForm.error("Username '" + formModel.username.trim()
                                + "' is already taken.");
                        target.add(userForm);
                        return;
                    }
                    User user = User.builder()
                            .username(formModel.username.trim())
                            .displayName(formModel.displayName)
                            .email(formModel.email)
                            .passwordHash(DefaultAuthService.hashPassword(formModel.password))
                            .roles(roles)
                            .active(formModel.active)
                            .maxSessions(formModel.maxSessions)
                            .timezone(formModel.timezone)
                            .build();
                    repo.save(user);
                    log.info("User '{}' created by '{}'", formModel.username.trim(),
                            FixSimulatorSession.get().getAuthenticatedUser() != null
                                    ? FixSimulatorSession.get().getAuthenticatedUser().username() : "?");
                } else {
                    User existing = repo.findByUsername(formModel.editingUsername)
                            .orElseThrow(() -> new IllegalStateException(
                                    "User not found: " + formModel.editingUsername));
                    String hash = (formModel.password != null && !formModel.password.isBlank())
                            ? DefaultAuthService.hashPassword(formModel.password)
                            : existing.passwordHash();
                    User updated = existing.toBuilder()
                            .displayName(formModel.displayName)
                            .email(formModel.email)
                            .passwordHash(hash)
                            .roles(roles)
                            .active(formModel.active)
                            .maxSessions(formModel.maxSessions)
                            .timezone(formModel.timezone)
                            .build();
                    repo.save(updated);
                    log.info("User '{}' updated by '{}'", formModel.editingUsername,
                            FixSimulatorSession.get().getAuthenticatedUser() != null
                                    ? FixSimulatorSession.get().getAuthenticatedUser().username() : "?");
                }

                target.add(listContainer, userForm, pageFeedback);
                target.appendJavaScript(
                        "bootstrap.Modal.getInstance(document.getElementById('userModal')).hide();");
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(userForm);
            }
        });
    }

    // ── Timezone label ─────────────────────────────────────────────────────────

    private static String tzLabel(String id) {
        if (id == null) return "(server default — UTC)";
        try {
            ZoneOffset offset = ZonedDateTime.now(ZoneId.of(id)).getOffset();
            String sign   = offset.getTotalSeconds() >= 0 ? "+" : "";
            String hhmm   = offset.getId().equals("Z") ? "+00:00" : offset.getId();
            return "(UTC" + sign + hhmm + ") " + id;
        } catch (Exception e) {
            return id;
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private String validateForm() {
        boolean isNew = (formModel.editingUsername == null);

        if (isNew) {
            if (formModel.username == null || formModel.username.isBlank())
                return "Username is required.";
            if (!formModel.username.matches("[a-zA-Z0-9_\\-\\.]+"))
                return "Username may only contain letters, digits, underscores, hyphens, and dots.";
            if (formModel.password == null || formModel.password.isBlank())
                return "Password is required for new users.";
        }

        if (formModel.password != null && !formModel.password.isBlank()) {
            if (formModel.password.length() < 8)
                return "Password must be at least 8 characters.";
            if (!formModel.password.equals(formModel.confirmPassword))
                return "Passwords do not match.";
        }

        if (!formModel.roleAdmin && !formModel.roleTester)
            return "At least one role must be assigned.";

        if (formModel.editingUsername != null && !formModel.roleAdmin
                && isLastAdmin(formModel.editingUsername))
            return "Cannot remove the Admin role from the only remaining admin user.";

        return null;
    }

    private String validateDelete(String username) {
        if (isSelf(username))
            return "You cannot delete your own account.";
        if (isLastAdmin(username))
            return "Cannot delete the only remaining Admin user.";
        return null;
    }

    private boolean isSelf(String username) {
        User me = FixSimulatorSession.get().getAuthenticatedUser();
        return me != null && me.username().equals(username);
    }

    private boolean isLastAdmin(String username) {
        UserRepository repo = app().getUserRepository();
        if (repo == null) return false;
        long adminCount = repo.findAll().stream()
                .filter(u -> u.hasRole(RoleRegistry.ADMIN))
                .count();
        if (adminCount > 1) return false;
        return repo.findByUsername(username)
                .map(u -> u.hasRole(RoleRegistry.ADMIN))
                .orElse(false);
    }

    // ── Form model ────────────────────────────────────────────────────────────

    static final class UserFormModel implements Serializable {
        private static final long serialVersionUID = 1L;

        String  editingUsername = null; // null = new user
        String  username        = "";
        String  displayName     = "";
        String  email           = "";
        String  password        = "";
        String  confirmPassword = "";
        boolean roleAdmin       = false;
        boolean roleTester      = false;
        boolean active          = true;
        int     maxSessions     = 0;
        String  timezone        = null;

        void loadFrom(User u) {
            editingUsername = u.username();
            username        = u.username();
            displayName     = u.displayName() != null ? u.displayName() : "";
            email           = u.email() != null ? u.email() : "";
            password        = "";
            confirmPassword = "";
            roleAdmin       = u.hasRole(RoleRegistry.ADMIN);
            roleTester      = u.hasRole(RoleRegistry.TESTER);
            active          = u.isActive();
            maxSessions     = u.maxSessions();
            timezone        = u.timezone();
        }

        void reset() {
            editingUsername = null;
            username        = "";
            displayName     = "";
            email           = "";
            password        = "";
            confirmPassword = "";
            roleAdmin       = false;
            roleTester      = false;
            active          = true;
            maxSessions     = 0;
            timezone        = null;
        }

        List<String> collectRoles() {
            List<String> r = new ArrayList<>();
            if (roleAdmin)  r.add(RoleRegistry.ADMIN);
            if (roleTester) r.add(RoleRegistry.TESTER);
            return r;
        }
    }
}
