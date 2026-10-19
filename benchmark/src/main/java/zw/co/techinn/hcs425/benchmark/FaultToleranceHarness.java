package zw.co.techinn.hcs425.benchmark;

import zw.co.techinn.hcs425.client.KeyValueClient;
import zw.co.techinn.hcs425.server.KeyValueServer;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Actual client-abort scenario: one socket is closed mid-frame while nine clients continue. */
public final class FaultToleranceHarness {
    private FaultToleranceHarness() { }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("hcs425.fault.results", "results/csv/fault-tolerance.txt"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (KeyValueServer server = new KeyValueServer("127.0.0.1", 0, 16, "fault-test")) {
            server.start();
            List<KeyValueClient> clients = new ArrayList<>();
            for (int i = 0; i < 10; i++) { KeyValueClient c = new KeyValueClient("127.0.0.1", server.port()); c.connect(); clients.add(c); }
            try (Socket aborted = new Socket("127.0.0.1", server.port())) {
                OutputStream out = aborted.getOutputStream(); out.write("PUT incomplete".getBytes(StandardCharsets.UTF_8)); out.flush();
            }
            int successful = 0;
            for (int i = 1; i < clients.size(); i++) if (clients.get(i).ping().success()) successful++;
            clients.get(0).close();
            try (KeyValueClient replacement = new KeyValueClient("127.0.0.1", server.port())) {
                boolean replacementWorks = replacement.put("replacement", "online").success();
                String evidence = "aborted_socket=true\nremaining_clients=" + successful + "\nreplacement_client_success=" + replacementWorks +
                        "\nserver_still_running=" + server.isRunning() + "\nactive_connections_after_cleanup=" + server.metrics().activeConnections() + "\n";
                Files.writeString(output, evidence);
                System.out.print(evidence);
            }
            clients.forEach(KeyValueClient::close);
        }
    }
}
