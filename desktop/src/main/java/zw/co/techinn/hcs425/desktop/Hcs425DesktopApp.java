package zw.co.techinn.hcs425.desktop;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import zw.co.techinn.hcs425.benchmark.BenchmarkResult;
import zw.co.techinn.hcs425.benchmark.BenchmarkRunner;
import zw.co.techinn.hcs425.client.AsyncKeyValueClient;
import zw.co.techinn.hcs425.client.ClientException;
import zw.co.techinn.hcs425.client.KeyValueClient;
import zw.co.techinn.hcs425.server.ClientConnectionInfo;
import zw.co.techinn.hcs425.server.KeyValueServer;
import zw.co.techinn.hcs425.server.ServerLogEntry;
import zw.co.techinn.hcs425.shared.ListEntry;
import zw.co.techinn.hcs425.shared.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** JavaFX monitoring console; network work is always delegated away from the FX thread. */
public final class Hcs425DesktopApp extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final ExecutorService background = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "hcs425-ui-worker"); t.setDaemon(true); return t; });
    private final ObservableList<Activity> activities = FXCollections.observableArrayList();
    private final ObservableList<BenchmarkResult> benchmarkResults = FXCollections.observableArrayList();
    private final String host = System.getProperty("hcs425.host", "127.0.0.1");
    private final int port = Integer.getInteger("hcs425.port", 5050);
    private final int workerCount = Integer.getInteger("hcs425.workers", 32);
    private KeyValueServer embeddedServer;
    private KeyValueClient client;
    private AsyncKeyValueClient asyncClient;
    private Stage stage;
    private BorderPane shell;
    private VBox sidebar;
    private VBox navigation;
    private Label connectionStatus;
    private Label pageTitle;
    private Label pageSubtitle;
    private boolean dark = !"light".equalsIgnoreCase(System.getProperty("hcs425.theme", "light"));
    private Timeline refresh;
    private Timeline dashboardTimeline;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        Application.setUserAgentStylesheet((dark ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
        startLocalServerIfAvailable();
        client = new KeyValueClient(host, port, 3_000);
        asyncClient = new AsyncKeyValueClient(client);
        shell = new BorderPane();
        shell.getStyleClass().add(dark ? "dark-theme" : "light-theme");
        shell.setLeft(buildSidebar());
        shell.setTop(buildHeader());
        showPage("Dashboard");
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        primaryStage.setTitle("HCS425 Distributed Key-Value System");
        primaryStage.setMinWidth(980); primaryStage.setMinHeight(640);
        primaryStage.setScene(scene); primaryStage.show();
        refresh = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshConnection()));
        refresh.setCycleCount(Timeline.INDEFINITE); refresh.play();
        refreshConnection();
    }

    private void startLocalServerIfAvailable() {
        if (!Boolean.parseBoolean(System.getProperty("hcs425.embedded", "true"))) return;
        try {
            embeddedServer = new KeyValueServer(host, port, workerCount, "desktop-local");
            embeddedServer.start();
        } catch (IOException ignored) {
            embeddedServer = null; // an independently launched server owns the configured port
        }
    }

    private VBox buildSidebar() {
        sidebar = new VBox(7); sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(210);
        Label mark = new Label("H"); mark.getStyleClass().add("brand-mark");
        Label brand = new Label("HCS425"); brand.getStyleClass().add("brand");
        Label subtitle = new Label("DISTRIBUTED SYSTEM\nOBSERVABILITY CONSOLE"); subtitle.getStyleClass().add("brand-subtitle");
        HBox brandRow = new HBox(9, mark, new VBox(2, brand, subtitle)); brandRow.setAlignment(Pos.CENTER_LEFT); brandRow.getStyleClass().add("brand-row");
        sidebar.getChildren().addAll(brandRow, new Separator(), styledLabel("MONITORING", "nav-label"));
        navigation = new VBox(4);
        String[] pages = {"Dashboard", "Key-Value Store", "Client Manager", "Performance Testing", "Server Logs", "Replication"};
        String[] glyphs = {"▦", "◇", "♙", "◒", "≡", "⎇"};
        for (int i = 0; i < pages.length; i++) {
            Button button = new Button(glyphs[i] + "   " + pages[i]); button.setMaxWidth(Double.MAX_VALUE); button.setUserData(pages[i]); button.getStyleClass().add("nav-button");
            button.setOnAction(e -> { showPage((String) button.getUserData()); navigation.getChildren().forEach(n -> n.getStyleClass().remove("selected")); button.getStyleClass().add("selected"); });
            navigation.getChildren().add(button);
            if (i == 0) button.getStyleClass().add("selected");
        }
        VBox.setVgrow(navigation, Priority.ALWAYS);
        ToggleButton theme = new ToggleButton("☾  Dark mode"); theme.setMaxWidth(Double.MAX_VALUE); theme.setSelected(dark);
        theme.setOnAction(e -> { dark = theme.isSelected(); applyTheme(dark); theme.setText(dark ? "☾  Dark mode" : "☀  Light mode"); });
        connectionStatus = new Label("● Connecting…"); connectionStatus.getStyleClass().addAll("status-offline", "sidebar-status");
        sidebar.getChildren().addAll(navigation, new Separator(), connectionStatus, theme);
        return sidebar;
    }

    private void applyTheme(boolean darkMode) {
        shell.getStyleClass().removeAll("dark-theme", "light-theme");
        shell.getStyleClass().add(darkMode ? "dark-theme" : "light-theme");
        Application.setUserAgentStylesheet((darkMode ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
    }

    private HBox buildHeader() {
        pageTitle = new Label(); pageTitle.getStyleClass().add("page-title");
        pageSubtitle = new Label(); pageSubtitle.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(2, pageTitle, pageSubtitle);
        ComboBox<String> node = new ComboBox<>(FXCollections.observableArrayList("desktop-local", "Server A · Leader", "Server B · Replica", "Server C · Replica")); node.setValue("desktop-local"); node.getStyleClass().add("toolbar-control"); node.setPrefWidth(150);
        Label live = new Label("● LIVE"); live.getStyleClass().add("live-badge");
        ToggleGroup ranges = new ToggleGroup(); HBox rangeBox = new HBox();
        for (String range : new String[]{"1m", "5m", "15m", "1h"}) { ToggleButton button = new ToggleButton(range); button.setToggleGroup(ranges); button.getStyleClass().add("range-button"); if ("5m".equals(range)) button.setSelected(true); rangeBox.getChildren().add(button); }
        Label rangeLabel = styledLabel("RANGE", "toolbar-label"); Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(14, titles, spacer, styledLabel("NODE", "toolbar-label"), node, live, rangeLabel, rangeBox); header.setAlignment(Pos.CENTER_LEFT); header.getStyleClass().add("topbar");
        return header;
    }

    private void showPage(String page) {
        if (dashboardTimeline != null) { dashboardTimeline.stop(); dashboardTimeline = null; }
        pageTitle.setText(page);
        pageSubtitle.setText(switch (page) {
            case "Dashboard" -> "Live server health, request activity and throughput";
            case "Key-Value Store" -> "Execute real TCP operations against the shared store";
            case "Client Manager" -> "Observe connections and exercise concurrent clients";
            case "Performance Testing" -> "Measure fixed-total-workload concurrency on the VPS";
            case "Server Logs" -> "Search and export server-side lifecycle and request events";
            default -> "Leader-coordinated replication demonstration and health";
        });
        Node content = switch (page) {
            case "Dashboard" -> dashboardPage();
            case "Key-Value Store" -> keyValuePage();
            case "Client Manager" -> clientsPage();
            case "Performance Testing" -> performancePage();
            case "Server Logs" -> logsPage();
            default -> replicationPage();
        };
        shell.setCenter(content);
    }

    private ScrollPane pagePane(Node node) { ScrollPane scroll = new ScrollPane(node); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("content"); return scroll; }

    private Node dashboardPage() {
        VBox box = new VBox(12); box.getStyleClass().add("observability-content");
        HBox strip = new HBox(8, styledLabel("OVERVIEW", "section-title"), styledLabel("live TCP telemetry · refreshed every 2 seconds", "section-meta")); strip.setAlignment(Pos.BASELINE_LEFT); strip.getStyleClass().add("section-strip");
        GridPane cards = new GridPane(); cards.setHgap(8); cards.setVgap(8);
        Label[] values = new Label[8]; String[] labels = {"SERVER STATUS", "ACTIVE CLIENTS", "TOTAL REQUESTS", "SUCCESSFUL", "FAILED", "AVG RESPONSE", "THROUGHPUT", "CURRENT NODE"}; String[] accents = {"metric-green", "metric-blue", "metric-cyan", "metric-green", "metric-red", "metric-orange", "metric-purple", "metric-teal"};
        for (int i = 0; i < values.length; i++) {
            values[i] = new Label("—"); values[i].getStyleClass().add("metric-value"); if (i == 0) values[i].getStyleClass().add("status-offline");
            VBox card = new VBox(5, styledLabel(labels[i], "metric-label"), values[i], styledLabel(i == 0 ? "TCP socket health" : "live measurement", "metric-subtext")); card.getStyleClass().addAll("metric-card", accents[i]);
            int column = i < 6 ? i : i - 6; int row = i < 6 ? 0 : 1; cards.add(card, column, row); GridPane.setHgrow(card, Priority.ALWAYS);
        }
        HBox chartHeader = new HBox(8, styledLabel("REQUEST TELEMETRY", "section-title"), styledLabel("measured server metrics", "section-meta")); chartHeader.setAlignment(Pos.BASELINE_LEFT);
        LineChart<String, Number> throughput = compactChart("Throughput · ops/s", "ops/s"); LineChart<String, Number> latency = compactChart("Latency · average ms", "ms");
        XYChart.Series<String, Number> throughputSeries = new XYChart.Series<>(); throughputSeries.setName("throughput"); throughput.getData().add(throughputSeries);
        XYChart.Series<String, Number> latencySeries = new XYChart.Series<>(); latencySeries.setName("latency"); latency.getData().add(latencySeries);
        GridPane charts = new GridPane(); charts.setHgap(8); charts.add(chartPanel("THROUGHPUT", throughput, "legend-blue"), 0, 0); charts.add(chartPanel("LATENCY", latency, "legend-cyan"), 1, 0); GridPane.setHgrow(charts.getChildren().get(0), Priority.ALWAYS); GridPane.setHgrow(charts.getChildren().get(1), Priority.ALWAYS);
        TableView<Activity> table = activityTable(); TitledPane activityPane = new TitledPane("RECENT ACTIVITY · " + activities.size() + " events", table); activityPane.setExpanded(true); activityPane.setPrefHeight(190); activityPane.setMaxWidth(Double.MAX_VALUE);
        VBox health = healthPanel(values); HBox lower = new HBox(8, activityPane, health); HBox.setHgrow(activityPane, Priority.ALWAYS); lower.setPrefHeight(190);
        box.getChildren().addAll(strip, cards, chartHeader, charts, lower);
        refreshDashboard(values, throughputSeries, latencySeries);
        dashboardTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshDashboard(values, throughputSeries, latencySeries))); dashboardTimeline.setCycleCount(Timeline.INDEFINITE); dashboardTimeline.play();
        return pagePane(box);
    }

    private TableView<Activity> activityTable() {
        TableView<Activity> table = new TableView<>(activities); table.setPrefHeight(190); table.setPlaceholder(new Label("No operations yet"));
        table.getColumns().add(column("Time", a -> a.time)); table.getColumns().add(column("Operation", a -> a.operation)); table.getColumns().add(column("Result", a -> a.result)); table.getColumns().add(column("Latency", a -> a.latency)); return table;
    }

    private Node keyValuePage() {
        VBox box = new VBox(16); box.getStyleClass().add("content");
        TextField key = new TextField(); key.setPromptText("student01"); TextField value = new TextField(); value.setPromptText("Value (spaces supported)");
        Label result = new Label("Ready"); result.getStyleClass().add("hint");
        TableView<ListEntry> table = new TableView<>(); table.getColumns().add(column("Key", e -> e.key())); table.getColumns().add(column("Value", e -> e.value()));
        HBox inputs = new HBox(12, labeledField("Key", key), labeledField("Value", value));
        Button put = button("PUT", "action-button"), get = button("GET", "action-button"), delete = button("DELETE", "danger-button"), list = button("LIST", "action-button");
        put.setOnAction(e -> runOperation(result, "PUT", () -> client.put(requireKey(key), value.getText()), table));
        get.setOnAction(e -> runOperation(result, "GET", () -> client.get(requireKey(key)), table));
        delete.setOnAction(e -> runOperation(result, "DELETE", () -> client.delete(requireKey(key)), table));
        list.setOnAction(e -> runOperation(result, "LIST", client::list, table));
        box.getChildren().addAll(new Label("Use one persistent TCP connection for interactive work. Values may contain spaces but not newlines."), inputs, new HBox(10, put, get, delete, list), result, table);
        return pagePane(box);
    }

    private Node clientsPage() {
        VBox box = new VBox(16); box.getStyleClass().add("content");
        TableView<ClientConnectionInfo> table = new TableView<>(); table.getColumns().add(column("Client ID", ClientConnectionInfo::clientId)); table.getColumns().add(column("Remote", ClientConnectionInfo::remoteAddress)); table.getColumns().add(column("Connected", c -> TIME.format(c.connectedAt()))); table.getColumns().add(column("Requests", c -> Long.toString(c.requestCount()))); table.getColumns().add(column("Last activity", c -> TIME.format(c.lastActivity())));
        Label status = new Label("Live view refreshes every two seconds"); status.getStyleClass().add("hint");
        Spinner<Integer> count = new Spinner<>(1, 100, 10); Button simulate = button("Launch simulated clients", "action-button"); simulate.setOnAction(e -> simulateClients(count.getValue(), status));
        box.getChildren().addAll(new HBox(10, new Label("Concurrent clients"), count, simulate), status, table);
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(2), e -> { if (embeddedServer != null) table.getItems().setAll(embeddedServer.clients()); })); t.setCycleCount(Timeline.INDEFINITE); t.play();
        return pagePane(box);
    }

    private Node performancePage() {
        VBox box = new VBox(16); box.getStyleClass().add("content");
        ComboBox<Integer> clients = new ComboBox<>(FXCollections.observableArrayList(1, 2, 5, 10, 20, 50, 100)); clients.setValue(10);
        Spinner<Integer> operations = new Spinner<>(100, 20_000, 2_000, 100); Label status = new Label("Fixed-total workload; read/write mix is measured by BenchmarkRunner."); status.getStyleClass().add("hint");
        TableView<BenchmarkResult> table = new TableView<>(benchmarkResults); table.getColumns().add(column("Clients", r -> Integer.toString(r.clients()))); table.getColumns().add(column("Operations", r -> Integer.toString(r.operations()))); table.getColumns().add(column("Elapsed", r -> String.format("%.3fs", r.elapsedSeconds()))); table.getColumns().add(column("Mean", r -> String.format("%.3fms", r.meanLatencyMs()))); table.getColumns().add(column("p95", r -> String.format("%.3fms", r.p95LatencyMs()))); table.getColumns().add(column("Throughput", r -> String.format("%.1f", r.throughputOpsSec()))); table.getColumns().add(column("Speedup", r -> String.format("%.2fx", r.speedup()))); table.getColumns().add(column("Efficiency", r -> String.format("%.1f%%", r.efficiencyPercent()))); table.getColumns().add(column("Errors", r -> Integer.toString(r.errors())));
        Button start = button("Start test", "action-button"); Button export = button("Export CSV", "action-button"); start.setOnAction(e -> { start.setDisable(true); status.setText("Running real TCP benchmark…"); background.submit(() -> { try { BenchmarkResult r = BenchmarkRunner.runOnce(port, clients.getValue(), operations.getValue()); Platform.runLater(() -> { benchmarkResults.add(r); status.setText("Completed: " + r.operations() + " operations, " + String.format("%.1f ops/s", r.throughputOpsSec())); start.setDisable(false); }); } catch (Exception ex) { Platform.runLater(() -> { status.setText("Benchmark failed: " + ex.getMessage()); start.setDisable(false); }); } }); });
        export.setOnAction(e -> exportBenchmark(status));
        box.getChildren().addAll(new HBox(12, new Label("Clients"), clients, new Label("Operations"), operations, start, export), status, table);
        return pagePane(box);
    }

    private Node logsPage() {
        VBox box = new VBox(14); box.getStyleClass().add("content"); TextField filter = new TextField(); filter.setPromptText("Search logs"); TextArea area = new TextArea(); area.setEditable(false); area.setPrefRowCount(24); Button export = button("Export logs", "action-button"); Runnable update = () -> { String query = filter.getText().toLowerCase(); List<String> lines = embeddedServer == null ? activities.stream().map(Activity::toString).toList() : embeddedServer.logs().stream().map(ServerLogEntry::toString).filter(s -> s.toLowerCase().contains(query)).toList(); area.setText(String.join("\n", lines)); }; filter.textProperty().addListener((obs, old, val) -> update.run()); export.setOnAction(e -> { try { Path out = Path.of("logs/ui-export.log"); Files.createDirectories(out.getParent()); Files.writeString(out, area.getText()); area.setText(area.getText() + "\nExported to " + out); } catch (IOException ex) { area.setText("Export failed: " + ex.getMessage()); } }); box.getChildren().addAll(new HBox(10, filter, export), area); update.run(); return pagePane(box);
    }

    private Node replicationPage() {
        VBox box = new VBox(16); box.getStyleClass().add("content");
        Label note = new Label("The cluster uses a single-writer leader. Writes are acknowledged after the leader's local ConcurrentHashMap update and propagated asynchronously to replicas; this is eventual consistency, not quorum durability."); note.setWrapText(true); note.getStyleClass().add("hint");
        TableView<NodeStatus> table = new TableView<>(); table.getColumns().add(column("Node", n -> n.name)); table.getColumns().add(column("Role", n -> n.role)); table.getColumns().add(column("Port", n -> Integer.toString(n.port))); table.getColumns().add(column("Health", n -> n.health));
        ObservableList<NodeStatus> nodes = FXCollections.observableArrayList(new NodeStatus("Server A", "Leader", 5050, "Checking…"), new NodeStatus("Server B", "Replica", 5051, "Checking…"), new NodeStatus("Server C", "Replica", 5052, "Checking…")); table.setItems(nodes);
        Button check = button("Check cluster health", "action-button"); check.setOnAction(e -> background.submit(() -> { for (NodeStatus node : nodes) { try (KeyValueClient c = new KeyValueClient(host, node.port, 700)) { node.health = c.ping().success() ? "Online" : "Error"; } catch (Exception ex) { node.health = "Offline"; } } Platform.runLater(table::refresh); }));
        box.getChildren().addAll(note, check, table, new Label("Failover policy: controlled operator failover. Automatic election is intentionally not claimed, avoiding split-brain writes without consensus.")); return pagePane(box);
    }

    private LineChart<String, Number> compactChart(String title, String axisLabel) {
        NumberAxis y = new NumberAxis(); CategoryAxis x = new CategoryAxis(); y.setLabel(axisLabel);
        LineChart<String, Number> chart = new LineChart<>(x, y); chart.setTitle(title); chart.setLegendVisible(false); chart.setAnimated(false); chart.setCreateSymbols(false); chart.setPrefHeight(165); chart.getStyleClass().add("chart"); return chart;
    }

    private VBox chartPanel(String title, LineChart<String, Number> chart, String legendStyle) {
        Label heading = styledLabel(title, "panel-title"); Label legend = styledLabel("● " + chart.getTitle(), "legend-item"); legend.getStyleClass().add(legendStyle);
        VBox panel = new VBox(3, heading, legend, chart); panel.getStyleClass().add("chart-panel"); VBox.setVgrow(chart, Priority.ALWAYS); return panel;
    }

    private VBox healthPanel(Label[] values) {
        VBox panel = new VBox(3); panel.getStyleClass().add("panel"); panel.setPrefWidth(220);
        panel.getChildren().addAll(styledLabel("SYSTEM HEALTH", "panel-title"), styledLabel("real-time node checks", "panel-meta"), healthRow("TCP server", values[0], false), healthRow("Active clients", values[1], false), healthRow("Current node", values[7], false), healthRow("Failed requests", values[4], true));
        return panel;
    }

    private HBox healthRow(String name, Label value, boolean warning) {
        Label dot = new Label("●"); dot.getStyleClass().add("health-dot"); if (warning) dot.getStyleClass().add("warn");
        Label label = styledLabel(name, "health-name");
        Label valueCopy = styledLabel("—", "health-value");
        valueCopy.textProperty().bind(value.textProperty());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(7, dot, label, spacer, valueCopy); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("health-row"); return row;
    }

    private void refreshDashboard(Label[] values, XYChart.Series<String, Number> throughputSeries, XYChart.Series<String, Number> latencySeries) {
        background.submit(() -> { try { Response response = client.stats(); String stats = response.message(); Platform.runLater(() -> {
            values[0].setText("ONLINE"); values[0].getStyleClass().remove("status-offline"); values[0].getStyleClass().add("status-online");
            values[7].setText(findStat(stats, "node")); values[1].setText(findStat(stats, "active")); values[2].setText(findStat(stats, "requests")); values[3].setText(findStat(stats, "success")); values[4].setText(findStat(stats, "failed")); values[5].setText(findStat(stats, "avgLatencyMs") + " ms"); values[6].setText(findStat(stats, "throughputOpsSec") + " ops/s");
            String stamp = TIME.format(java.time.Instant.now()); double throughput = numericStat(stats, "throughputOpsSec"); double latency = numericStat(stats, "avgLatencyMs"); throughputSeries.getData().add(new XYChart.Data<>(stamp, throughput)); latencySeries.getData().add(new XYChart.Data<>(stamp, latency));
            if (throughputSeries.getData().size() > 30) throughputSeries.getData().remove(0); if (latencySeries.getData().size() > 30) latencySeries.getData().remove(0);
        }); } catch (Exception ex) { Platform.runLater(() -> { values[0].setText("OFFLINE"); values[0].getStyleClass().remove("status-online"); values[0].getStyleClass().add("status-offline"); }); } });
    }

    private void refreshConnection() { background.submit(() -> { try { client.ping(); Platform.runLater(() -> { connectionStatus.setText("● Server online"); connectionStatus.getStyleClass().remove("status-offline"); connectionStatus.getStyleClass().add("status-online"); }); } catch (Exception ex) { Platform.runLater(() -> { connectionStatus.setText("● Server offline"); connectionStatus.getStyleClass().remove("status-online"); connectionStatus.getStyleClass().add("status-offline"); }); } }); }

    private void runOperation(Label result, String operation, CheckedCall call, TableView<ListEntry> table) { long start = System.nanoTime(); background.submit(() -> { try { Response response = call.run(); double ms = (System.nanoTime() - start) / 1_000_000d; Platform.runLater(() -> { result.setText(response.success() ? response.message() + (response.value() == null ? "" : ": " + response.value()) + "  •  " + String.format("%.2f ms", ms) : "ERROR " + response.message()); if ("LIST".equals(response.message())) table.getItems().setAll(response.entries()); activities.add(new Activity(TIME.format(java.time.Instant.now()), operation, response.message(), String.format("%.2f ms", ms), ms)); }); } catch (Exception ex) { Platform.runLater(() -> { result.setText("ERROR " + ex.getMessage()); activities.add(new Activity(TIME.format(java.time.Instant.now()), operation, "ERROR", "—", 0)); }); } }); }

    private void simulateClients(int count, Label status) { status.setText("Launching " + count + " simulated clients…"); background.submit(() -> { List<CompletableFuture<Void>> jobs = new ArrayList<>(); for (int i = 0; i < count; i++) { int id = i; jobs.add(CompletableFuture.runAsync(() -> { try (KeyValueClient c = new KeyValueClient(host, port, 3_000)) { for (int n = 0; n < 5; n++) c.put("sim-" + id + "-" + n, "client-" + id); } catch (Exception ignored) { } }, background)); } CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).whenComplete((v, ex) -> Platform.runLater(() -> status.setText("Completed simulated-client run: " + count + " clients"))); }); }

    private void exportBenchmark(Label status) { try { Path out = Path.of("results/csv/ui-benchmark-results.csv"); Files.createDirectories(out.getParent()); StringBuilder csv = new StringBuilder("clients,operations,elapsed_seconds,mean_latency_ms,median_latency_ms,p95_latency_ms,throughput_ops_sec,speedup,efficiency_percent,errors\n"); benchmarkResults.forEach(r -> csv.append(String.format(java.util.Locale.ROOT, "%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n", r.clients(),r.operations(),r.elapsedSeconds(),r.meanLatencyMs(),r.medianLatencyMs(),r.p95LatencyMs(),r.throughputOpsSec(),r.speedup(),r.efficiencyPercent(),r.errors()))); Files.writeString(out, csv.toString()); status.setText("Exported " + benchmarkResults.size() + " rows to " + out); } catch (IOException ex) { status.setText("Export failed: " + ex.getMessage()); } }

    private String findStat(String stats, String name) { for (String token : stats.split(" ")) if (token.startsWith(name + "=")) return token.substring(name.length() + 1); return "—"; }
    private double numericStat(String stats, String name) { try { return Double.parseDouble(findStat(stats, name)); } catch (NumberFormatException ex) { return 0; } }
    private String requireKey(TextField field) { if (field.getText().isBlank() || field.getText().chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("Key must be non-empty and contain no spaces"); return field.getText(); }
    private Label styledLabel(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); return l; }
    private Label label(String text) { return styledLabel(text, "metric-label"); }
    private Button button(String text, String style) { Button b = new Button(text); b.getStyleClass().add(style); return b; }
    private VBox labeledField(String title, TextField field) { return new VBox(5, label(title), field); }
    private <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> function) { TableColumn<T, String> c = new TableColumn<>(title); c.setCellValueFactory(data -> new SimpleStringProperty(function.apply(data.getValue()))); c.setPrefWidth(150); return c; }

    @Override public void stop() { if (refresh != null) refresh.stop(); if (dashboardTimeline != null) dashboardTimeline.stop(); if (asyncClient != null) asyncClient.close(); if (embeddedServer != null) embeddedServer.close(); background.shutdownNow(); }
    @FunctionalInterface private interface CheckedCall { Response run() throws Exception; }
    private static final class Activity { final String time, operation, result, latency; final double latencyMs; Activity(String time, String operation, String result, String latency, double latencyMs) { this.time=time;this.operation=operation;this.result=result;this.latency=latency;this.latencyMs=latencyMs; } @Override public String toString(){return time+" "+operation+" "+result+" "+latency;} }
    private static final class NodeStatus { final String name, role; final int port; String health; NodeStatus(String n,String r,int p,String h){name=n;role=r;port=p;health=h;} }
}
