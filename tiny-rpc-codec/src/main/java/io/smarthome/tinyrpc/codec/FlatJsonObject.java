package io.smarthome.tinyrpc.codec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal parser/writer for an object whose values are strings or signed 64-bit integers. */
final class FlatJsonObject {
    private FlatJsonObject() { }

    static Map<String, Object> parse(String json) {
        Parser parser = new Parser(json);
        Map<String, Object> result = parser.parseObject();
        parser.skipWhitespace();
        if (!parser.finished()) throw parser.invalid("Trailing content");
        return result;
    }

    static String write(Map<String, Object> values) {
        StringBuilder json = new StringBuilder(256).append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            appendString(json, entry.getKey()); json.append(':');
            Object value = entry.getValue();
            if (value instanceof String) appendString(json, (String) value);
            else if (value instanceof Long || value instanceof Integer) json.append(value);
            else throw new IllegalArgumentException("RPC envelope values must be strings or integers");
        }
        return json.append('}').toString();
    }

    private static void appendString(StringBuilder target, String value) {
        if (value == null) throw new IllegalArgumentException("RPC envelope string must not be null");
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': target.append("\\\""); break;
                case '\\': target.append("\\\\"); break;
                case '\b': target.append("\\b"); break;
                case '\f': target.append("\\f"); break;
                case '\n': target.append("\\n"); break;
                case '\r': target.append("\\r"); break;
                case '\t': target.append("\\t"); break;
                default:
                    if (character < 0x20) appendUnicode(target, character);
                    else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))
                            throw new IllegalArgumentException("Unpaired high surrogate in RPC envelope");
                        target.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new IllegalArgumentException("Unpaired low surrogate in RPC envelope");
                    } else target.append(character);
            }
        }
        target.append('"');
    }

    private static void appendUnicode(StringBuilder target, char character) {
        target.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            target.append(Character.forDigit((character >> shift) & 0xf, 16));
        }
    }

    private static final class Parser {
        private final String json;
        private int position;

        private Parser(String json) { this.json = json; }

        private Map<String, Object> parseObject() {
            skipWhitespace(); expect('{'); skipWhitespace();
            Map<String, Object> values = new LinkedHashMap<>();
            if (take('}')) return values;
            while (true) {
                String key = parseString();
                if (values.containsKey(key)) throw invalid("Duplicate field: " + key);
                skipWhitespace(); expect(':'); skipWhitespace();
                Object value = peek() == '"' ? parseString() : parseInteger();
                values.put(key, value);
                skipWhitespace();
                if (take('}')) return values;
                expect(','); skipWhitespace();
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!finished()) {
                char character = json.charAt(position++);
                if (character == '"') {
                    validateSurrogates(value);
                    return value.toString();
                }
                if (character < 0x20) throw invalid("Unescaped control character");
                if (character != '\\') { value.append(character); continue; }
                if (finished()) throw invalid("Incomplete escape sequence");
                char escaped = json.charAt(position++);
                switch (escaped) {
                    case '"': case '\\': case '/': value.append(escaped); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u': value.append(parseUnicode()); break;
                    default: throw invalid("Unsupported escape sequence");
                }
            }
            throw invalid("Unterminated string");
        }

        private char parseUnicode() {
            if (position + 4 > json.length()) throw invalid("Incomplete Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(json.charAt(position++), 16);
                if (digit < 0) throw invalid("Invalid Unicode escape");
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Long parseInteger() {
            int start = position;
            if (take('-') && finished()) throw invalid("Incomplete integer");
            if (take('0')) {
                if (!finished() && Character.isDigit(peek())) throw invalid("Leading zero in integer");
            } else {
                if (finished() || peek() < '1' || peek() > '9') throw invalid("Expected string or integer value");
                while (!finished() && Character.isDigit(peek())) position++;
            }
            try { return Long.valueOf(json.substring(start, position)); }
            catch (NumberFormatException overflow) { throw invalid("Integer is outside signed 64-bit range"); }
        }

        private void expect(char expected) {
            if (finished() || json.charAt(position) != expected) throw invalid("Expected '" + expected + "'");
            position++;
        }

        private boolean take(char expected) {
            if (!finished() && json.charAt(position) == expected) { position++; return true; }
            return false;
        }

        private char peek() { return finished() ? '\0' : json.charAt(position); }
        private boolean finished() { return position >= json.length(); }
        private void skipWhitespace() {
            while (!finished()) {
                char value = json.charAt(position);
                if (value != ' ' && value != '\t' && value != '\n' && value != '\r') return;
                position++;
            }
        }
        private IllegalArgumentException invalid(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + position);
        }
    }

    private static void validateSurrogates(CharSequence value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))
                    throw new IllegalArgumentException("Unpaired high surrogate in RPC envelope");
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("Unpaired low surrogate in RPC envelope");
            }
        }
    }
}
