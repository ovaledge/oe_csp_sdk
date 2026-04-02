package com.ovaledge.csp.apps.app.generator;

import java.util.Map;

public class TemplateEngine {

    public String render(String template, Map<String, String> values) {
        String output = template;
        if (values == null || values.isEmpty()) {
            return output;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            // Maven/archetype-style templates: ${key}
            String placeholder = "${" + key + "}";
            output = output.replace(placeholder, value);
            // Archetype literal placeholders: __key__ (only when key is wrapped in __)
            if (key.length() > 4 && key.startsWith("__") && key.endsWith("__")) {
                output = output.replace(key, value);
            }
        }
        return output;
    }
}
