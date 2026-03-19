package com.yoobu.api.audit;

import com.yoobu.api.audit.dto.AuditLogItemResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AuditLogChangeFormatter {

    public List<String> buildDiffLines(AuditLogItemResponse entry) {
        return buildDiffLines(entry.oldValue(), entry.newValue());
    }

    public String summarizeChanges(AuditLogItemResponse entry) {
        return String.join(" | ", buildDiffLines(entry));
    }

    public List<String> buildDiffLines(Object oldValue, Object newValue) {
        if (oldValue instanceof Map<?, ?> oldMap && newValue instanceof Map<?, ?> newMap) {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            oldMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            newMap.keySet().forEach(key -> keys.add(String.valueOf(key)));

            List<String> lines = new ArrayList<>();
            for (String key : keys) {
                Object left = oldMap.get(key);
                Object right = newMap.get(key);
                if (!Objects.equals(left, right)) {
                    lines.add(key + ": " + renderValue(left) + " -> " + renderValue(right));
                }
            }
            return lines.isEmpty() ? List.of("No top-level changes") : lines;
        }

        if (!Objects.equals(oldValue, newValue)) {
            return List.of("value: " + renderValue(oldValue) + " -> " + renderValue(newValue));
        }
        return List.of("No changes");
    }

    private String renderValue(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
