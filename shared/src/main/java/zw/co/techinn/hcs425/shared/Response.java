package zw.co.techinn.hcs425.shared;

import java.util.List;

public record Response(boolean success, String message, String value, List<ListEntry> entries) {
    public Response {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static Response ok(String message) { return new Response(true, message, null, List.of()); }
    public static Response value(String value) { return new Response(true, "VALUE", value, List.of()); }
    public static Response list(List<ListEntry> entries) { return new Response(true, "LIST", null, entries); }
    public static Response error(String code) { return new Response(false, code, null, List.of()); }
}
