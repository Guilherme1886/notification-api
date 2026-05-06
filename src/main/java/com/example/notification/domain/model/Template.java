package com.example.notification.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Template(
        String id,
        String name,
        NotificationChannel channel,
        String subject,
        String body,
        Instant createdAt
) {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    public Template {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public String renderBody(Map<String, String> variables) {
        return render(body, variables);
    }

    public String renderSubject(Map<String, String> variables) {
        return subject == null ? null : render(subject, variables);
    }

    private static String render(String template, Map<String, String> variables) {
        var matcher = VARIABLE.matcher(template);
        var sb = new StringBuilder();
        while (matcher.find()) {
            var key = matcher.group(1);
            var value = variables.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
