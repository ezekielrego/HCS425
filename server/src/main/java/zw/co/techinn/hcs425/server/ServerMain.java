package zw.co.techinn.hcs425.server;

public final class ServerMain {
    private ServerMain() { }
    public static void main(String[] args) throws Exception {
        String host = System.getProperty("hcs425.host", "127.0.0.1");
        int port = Integer.getInteger("hcs425.port", 5050);
        int workers = Integer.getInteger("hcs425.workers", 32);
        String node = System.getProperty("hcs425.node", "primary");
        KeyValueServer server = new KeyValueServer(host, port, workers, node);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "hcs425-shutdown"));
        server.start();
        Thread.currentThread().join();
    }
}
