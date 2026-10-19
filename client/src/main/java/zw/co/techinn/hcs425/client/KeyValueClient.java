package zw.co.techinn.hcs425.client;

import zw.co.techinn.hcs425.shared.ListEntry;
import zw.co.techinn.hcs425.shared.Protocol;
import zw.co.techinn.hcs425.shared.ProtocolException;
import zw.co.techinn.hcs425.shared.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Synchronous TCP client. Call it from a background executor when used by JavaFX. */
public final class KeyValueClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final int timeoutMillis;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public KeyValueClient(String host, int port) { this(host, port, 3_000); }
    public KeyValueClient(String host, int port, int timeoutMillis) {
        this.host = host; this.port = port; this.timeoutMillis = timeoutMillis;
    }

    public synchronized void connect() throws ClientException {
        if (isConnected()) return;
        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(host, port), timeoutMillis);
            newSocket.setSoTimeout(timeoutMillis);
            socket = newSocket;
            reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            closeQuietly();
            throw new ClientException("Unable to connect to " + host + ":" + port, ex);
        }
    }

    public synchronized void reconnect() throws ClientException { closeQuietly(); connect(); }

    public synchronized boolean isConnected() { return socket != null && socket.isConnected() && !socket.isClosed(); }

    public synchronized Response put(String key, String value) throws ClientException { return send("PUT " + key + " " + value); }
    public synchronized Response get(String key) throws ClientException { return send("GET " + key); }
    public synchronized Response delete(String key) throws ClientException { return send("DELETE " + key); }
    public synchronized Response list() throws ClientException { return send("LIST"); }
    public synchronized Response ping() throws ClientException { return send("PING"); }
    public synchronized Response stats() throws ClientException { return send("STATS"); }

    public synchronized Response send(String command) throws ClientException {
        if (!isConnected()) connect();
        try {
            writer.write(command);
            writer.newLine();
            writer.flush();
            return Protocol.readResponse(reader);
        } catch (EOFException ex) {
            closeQuietly();
            throw new ClientException("Server closed the connection", ex);
        } catch (IOException | ProtocolException ex) {
            closeQuietly();
            throw new ClientException("Request failed: " + command, ex);
        }
    }

    public synchronized List<ListEntry> listEntries() throws ClientException {
        Response response = list();
        if (!response.success()) throw new ClientException(response.message());
        return response.entries();
    }

    @Override public synchronized void close() { closeQuietly(); }

    private void closeQuietly() {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        socket = null; reader = null; writer = null;
    }
}
