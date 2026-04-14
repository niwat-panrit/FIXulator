package com.example.components;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.convert.IConverter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * A TextField&lt;LocalDate&gt; that renders as an HTML5 &lt;input type="date"&gt;.
 *
 * Wicket's standard TextField enforces type="text" in markup; this subclass
 * temporarily swaps the tag type to satisfy that check, then restores "date"
 * so the browser renders its native date-picker — the same UX as JSF's
 * &lt;p:datePicker&gt; component.
 *
 * The built-in converter handles ISO-8601 date strings (yyyy-MM-dd), which is
 * exactly the value format that HTML date inputs produce and consume.
 */
public class LocalDateTextField extends TextField<LocalDate> {

    public LocalDateTextField(String id, IModel<LocalDate> model) {
        super(id, model, LocalDate.class);
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        // Temporarily set type="text" so TextField's type-check passes,
        // then restore type="date" for the actual rendered HTML.
        tag.put("type", "text");
        super.onComponentTag(tag);
        tag.put("type", "date");
    }

    @Override
    public <C> IConverter<C> getConverter(Class<C> type) {
        if (LocalDate.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            IConverter<C> converter = (IConverter<C>) new LocalDateConverter();
            return converter;
        }
        return super.getConverter(type);
    }

    private static class LocalDateConverter implements IConverter<LocalDate>, Serializable {
        @Override
        public LocalDate convertToObject(String value, Locale locale) {
            if (value == null || value.isBlank()) return null;
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                return null;
            }
        }

        @Override
        public String convertToString(LocalDate value, Locale locale) {
            return value != null ? value.toString() : "";
        }
    }
}
