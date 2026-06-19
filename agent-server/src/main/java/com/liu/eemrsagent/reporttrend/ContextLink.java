package com.liu.eemrsagent.reporttrend;

import java.util.List;

public record ContextLink(
        String type,
        List<String> symptoms,
        List<String> indicators,
        String note
) {
}
