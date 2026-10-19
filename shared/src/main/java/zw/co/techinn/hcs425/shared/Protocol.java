package zw.co.techinn.hcs425.shared;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Line-framed UTF-8 protocol. PUT values may contain spaces but may not contain
 * a newline. LIST uses a count plus ITEM frames and an END LIST terminator.
 */
public final class Protocol {
    public static final int MAX_LINE_LENGTH = 1_048_576;

    private Protocol() { }

    public static Request parseRequest(String line) throws ProtocolException {
        if (line == null || line.isBlank()) throw new ProtocolException("BAD_REQUEST empty command");
        if (line.length() > MAX_LINE_LENGTH) throw new ProtocolException("REQUEST_TOO_LARGE");
        String[] tokens = line.split("\\s+", 2);
        String command = tokens[0].toUpperCase();
        return switch (command) {
            case "PUT" -> parsePut(line);
            case "GET" -> parseSingleArgument(tokens, Operation.GET);
            case "DELETE" -> parseSingleArgument(tokens, Operation.DELETE);
            case "LIST" -> noArguments(tokens, Operation.LIST);
            case "PING" -> noArguments(tokens, Operation.PING);
            case "STATS" -> noArguments(tokens, Operation.STATS);
            case "REPL" -> parseReplication(line);
            default -> throw new ProtocolException("BAD_REQUEST unknown operation");
        };
    }

    private static Request parsePut(String line) throws ProtocolException {
        int firstSpace = line.indexOf(' ');
        if (firstSpace < 0) throw new ProtocolException("BAD_REQUEST PUT requires key and value");
        int secondSpace = line.indexOf(' ', firstSpace + 1);
        if (secondSpace < 0) throw new ProtocolException("BAD_REQUEST PUT requires key and value");
        String key = line.substring(firstSpace + 1, secondSpace);
        String value = line.substring(secondSpace + 1);
        validateKey(key);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new ProtocolException("BAD_REQUEST newline in value");
        return Request.put(key, value);
    }

    private static Request parseSingleArgument(String[] tokens, Operation operation) throws ProtocolException {
        if (tokens.length < 2 || tokens[1].isBlank() || tokens[1].contains(" ")) {
            throw new ProtocolException("BAD_REQUEST " + operation + " requires one key");
        }
        validateKey(tokens[1]);
        return new Request(operation, tokens[1], null, 0);
    }

    private static Request noArguments(String[] tokens, Operation operation) throws ProtocolException {
        if (tokens.length != 1) throw new ProtocolException("BAD_REQUEST " + operation + " takes no arguments");
        return new Request(operation, null, null, 0);
    }

    private static Request parseReplication(String line) throws ProtocolException {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 5 && tokens.length != 4) throw new ProtocolException("BAD_REQUEST malformed replication frame");
        try {
            long sequence = Long.parseLong(tokens[1]);
            Operation op = Operation.valueOf(tokens[2]);
            String key = decode(tokens[3]);
            String value = tokens.length == 5 ? decode(tokens[4]) : null;
            validateKey(key);
            if (op != Operation.PUT && op != Operation.DELETE) throw new ProtocolException("BAD_REQUEST invalid replication operation");
            if (op == Operation.PUT && value == null) throw new ProtocolException("BAD_REQUEST missing replication value");
            return new Request(Operation.REPLICATE, key, value, sequence);
        } catch (IllegalArgumentException ex) {
            throw new ProtocolException("BAD_REQUEST malformed replication frame");
        }
    }

    private static void validateKey(String key) throws ProtocolException {
        if (key == null || key.isBlank() || key.chars().anyMatch(Character::isWhitespace)) {
            throw new ProtocolException("BAD_REQUEST invalid key");
        }
    }

    public static String format(Response response) {
        StringBuilder out = new StringBuilder();
        if (!response.success()) {
            out.append("ERROR ").append(response.message()).append('\n');
        } else if ("LIST".equals(response.message())) {
            out.append("OK LIST ").append(response.entries().size()).append('\n');
            for (ListEntry entry : response.entries()) {
                out.append("ITEM ").append(encode(entry.key())).append(' ').append(encode(entry.value())).append('\n');
            }
            out.append("END LIST\n");
        } else if ("VALUE".equals(response.message())) {
            out.append("OK VALUE ").append(response.value() == null ? "" : response.value()).append('\n');
        } else {
            out.append("OK ").append(response.message()).append('\n');
        }
        return out.toString();
    }

    public static void write(BufferedWriter writer, Response response) throws IOException {
        writer.write(format(response));
        writer.flush();
    }

    public static Response readResponse(BufferedReader reader) throws IOException, ProtocolException {
        String first = reader.readLine();
        if (first == null) throw new EOFException("server closed connection");
        if (first.startsWith("ERROR ")) return Response.error(first.substring(6));
        if (!first.startsWith("OK ")) throw new ProtocolException("BAD_RESPONSE missing status");
        if (first.startsWith("OK LIST ")) {
            int count;
            try { count = Integer.parseInt(first.substring(8)); }
            catch (NumberFormatException ex) { throw new ProtocolException("BAD_RESPONSE invalid list count"); }
            if (count < 0 || count > 100_000) throw new ProtocolException("BAD_RESPONSE invalid list count");
            List<ListEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String item = reader.readLine();
                if (item == null || !item.startsWith("ITEM ")) throw new ProtocolException("BAD_RESPONSE missing list item");
                String[] parts = item.split(" ", 3);
                if (parts.length != 3) throw new ProtocolException("BAD_RESPONSE malformed list item");
                entries.add(new ListEntry(decode(parts[1]), decode(parts[2])));
            }
            if (!"END LIST".equals(reader.readLine())) throw new ProtocolException("BAD_RESPONSE missing list terminator");
            return Response.list(entries);
        }
        if (first.startsWith("OK VALUE")) {
            return Response.value(first.length() > 9 ? first.substring(9) : "");
        }
        return Response.ok(first.substring(3));
    }

    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) throws ProtocolException {
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { throw new ProtocolException("BAD_REQUEST invalid base64"); }
    }
}
