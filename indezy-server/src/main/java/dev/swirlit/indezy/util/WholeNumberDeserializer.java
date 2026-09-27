package dev.swirlit.indezy.util;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Rejects fractional JSON values instead of silently truncating an integer setting. */
public final class WholeNumberDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.isExpectedNumberIntToken()) {
            return context.reportInputMismatch(Integer.class, "Expected a whole number");
        }
        return parser.getIntValue();
    }
}
