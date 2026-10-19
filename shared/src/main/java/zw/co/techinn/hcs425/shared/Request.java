package zw.co.techinn.hcs425.shared;

public record Request(Operation operation, String key, String value, long sequence) {
    public static Request put(String key, String value) { return new Request(Operation.PUT, key, value, 0); }
    public static Request get(String key) { return new Request(Operation.GET, key, null, 0); }
    public static Request delete(String key) { return new Request(Operation.DELETE, key, null, 0); }
    public static Request list() { return new Request(Operation.LIST, null, null, 0); }
    public static Request ping() { return new Request(Operation.PING, null, null, 0); }
}
