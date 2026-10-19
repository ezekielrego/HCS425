package zw.co.techinn.hcs425.server;

import zw.co.techinn.hcs425.shared.ListEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe store. LIST returns a sorted snapshot to avoid exposing live map state. */
public final class KeyValueStore {
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

    public void put(String key, String value) { values.put(key, value); }
    public String get(String key) { return values.get(key); }
    public boolean contains(String key) { return values.containsKey(key); }
    public String delete(String key) { return values.remove(key); }
    public int size() { return values.size(); }

    public List<ListEntry> snapshot() {
        List<ListEntry> snapshot = new ArrayList<>();
        values.forEach((key, value) -> snapshot.add(new ListEntry(key, value)));
        snapshot.sort(Comparator.comparing(ListEntry::key));
        return List.copyOf(snapshot);
    }
}
