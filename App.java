package com.calculator.osproject.v2;



import java.util.List;
import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // ── State ──────────────────────────────────────────────────────────
    private final Scheduler scheduler  = new Scheduler();
    private int             pidCounter = 1;
    private Timeline        timeline;

    private static final Color[] COLORS = {
        Color.web("#4e79a7"), Color.web("#f28e2b"), Color.web("#e15759"),
        Color.web("#76b7b2"), Color.web("#59a14f"), Color.web("#edc948"),
        Color.web("#b07aa1"), Color.web("#ff9da7"), Color.web("#9c755f")
    };
    private int colorIdx = 0;

    private TableView<Process> table;

    // ── JavaFX entry point ─────────────────────────────────────────────
    @Override
    public void start(Stage stage) {

        // Algorithm selector
        Label algoLbl = new Label("Algorithm:");
        ComboBox<String> algoBox = new ComboBox<>();
        algoBox.getItems().addAll(
            "FCFS",
            "SJF – Non-Preemptive",
            "SJF – Preemptive (SRTF)",
            "Priority – Non-Preemptive",
            "Priority – Preemptive",
            "Round Robin (RR)"
        );
        algoBox.setValue("FCFS");
        algoBox.setPrefWidth(215);

        // Quantum input (RR only)
        Label quantumLbl = new Label("Quantum:");
        TextField quantumFld = numField("2", 55);
        HBox quantumRow = new HBox(6, quantumLbl, quantumFld);
        quantumRow.setAlignment(Pos.CENTER_LEFT);
        quantumRow.setVisible(false);
        quantumRow.setManaged(false);

        // Process input fields
        Label arrLbl  = new Label("Arrival:");
        Label brstLbl = new Label("Burst:");
        Label priLbl  = new Label("Priority (1-10):");

        TextField arrFld  = numField("0", 55);
        TextField brstFld = numField("5", 55);
        TextField priFld  = numField("1", 55);

        HBox priRow = new HBox(6, priLbl, priFld);
        priRow.setAlignment(Pos.CENTER_LEFT);
        priRow.setVisible(false);
        priRow.setManaged(false);

        algoBox.valueProperty().addListener((obs, o, n) -> {
            boolean isRR  = n.startsWith("Round Robin");
            boolean isPri = n.startsWith("Priority");
            quantumRow.setVisible(isRR);  quantumRow.setManaged(isRR);
            priRow.setVisible(isPri);     priRow.setManaged(isPri);
        });

        // Buttons
        Button addBtn   = new Button("➕  Add Process");
        Button startBtn = new Button("▶  Start");
        Button batchBtn = new Button("⚡  Run All");
        Button resetBtn = new Button("↺  Reset");

        styleBtn(addBtn,   "#4e79a7");
        styleBtn(startBtn, "#59a14f");
        styleBtn(batchBtn, "#e6820e");
        styleBtn(resetBtn, "#888888");

        // Input toolbar
        HBox inputRow = new HBox(10,
            algoLbl, algoBox,
            sep(), quantumRow,
            sep(), arrLbl, arrFld, brstLbl, brstFld, priRow,
            sep(), addBtn
        );
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(8, 10, 8, 10));
        inputRow.setStyle(
            "-fx-background-color: #f4f4f4;" +
            "-fx-border-color: #ddd;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Label statusLbl = new Label("");
        statusLbl.setFont(Font.font("System", FontWeight.NORMAL, 12));
        statusLbl.setStyle("-fx-text-fill: #555;");

        HBox controlRow = new HBox(10, startBtn, batchBtn, resetBtn, statusLbl);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        controlRow.setPadding(new Insets(6, 10, 6, 10));

        // ── Process table ──────────────────────────────────────────────
        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(190);
        table.setPlaceholder(new Label("No processes added yet."));

        table.getColumns().addAll(
            tcol("PID",        "pid",        65),
            tcol("Arrival",    "arrival",    65),
            tcol("Burst",      "burst",      60),
            tcol("Priority",   "priority",   62),
            tcol("Remaining",  "remaining",  75),
            tcol("Waiting",    "waiting",    65),
            tcol("Turnaround", "turnaround", 82)
        );

        // Delete button column
        TableColumn<Process, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(72);
        deleteCol.setSortable(false);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✕");
            {
                btn.setStyle(
                    "-fx-background-color: #e15759;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 11;" +
                    "-fx-padding: 2 8;" +
                    "-fx-background-radius: 3;"
                );
                btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
                btn.setOnMouseExited (e -> btn.setOpacity(1.0));
                btn.setOnAction(ev -> {
                    Process p = getTableView().getItems().get(getIndex());
                    scheduler.removeProcess(p);
                    getTableView().getItems().remove(p);
                    if (scheduler.getProcesses().isEmpty() && timeline != null) {
                        timeline.stop();
                        startBtn.setText("▶  Start");
                        statusLbl.setText("");
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
        table.getColumns().add(deleteCol);

        // ── Gantt chart ────────────────────────────────────────────────
        Label ganttTitle = new Label("  Gantt Chart");
        ganttTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        ganttTitle.setPadding(new Insets(6, 0, 2, 0));

        Pane ganttPane = new Pane();
        ganttPane.setPrefHeight(75);

        ScrollPane ganttScroll = new ScrollPane(ganttPane);
        ganttScroll.setPrefHeight(100);
        ganttScroll.setFitToHeight(false);
        ganttScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        ganttScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        ganttScroll.setStyle("-fx-background: white; -fx-border-color: #ddd;");

        // ── Metrics bar ────────────────────────────────────────────────
        Label avgWtLabel = new Label("Avg Waiting Time: —");
        Label avgTtLabel = new Label("Avg Turnaround Time: —");
        avgWtLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        avgTtLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        HBox metricsBar = new HBox(40, avgWtLabel, avgTtLabel);
        metricsBar.setPadding(new Insets(8, 14, 8, 14));
        metricsBar.setAlignment(Pos.CENTER_LEFT);
        metricsBar.setStyle(
            "-fx-background-color: #f0f7ff;" +
            "-fx-border-color: #c5d8f0;" +
            "-fx-border-width: 1 0 0 0;"
        );

        // ── Button logic ───────────────────────────────────────────────

        // ADD
        addBtn.setOnAction(e -> {
            int arrVal, brstVal, priVal;
            try {
                arrVal  = parseInt(arrFld.getText(),  0, 9999, "Arrival");
                brstVal = parseInt(brstFld.getText(), 1, 9999, "Burst");
                priVal  = parseInt(priFld.getText(),  1, 10,   "Priority (1–10)");
            } catch (IllegalArgumentException ex) {
                alert(ex.getMessage());
                return;
            }

            boolean running = timeline != null &&
                              timeline.getStatus() == Timeline.Status.RUNNING;
            if (running && arrVal < scheduler.getTime()) {
                arrVal = scheduler.getTime();
                arrFld.setText(String.valueOf(arrVal));
            }

            Process p = new Process("P" + pidCounter++, arrVal, brstVal, priVal);
            p.colorIndex = colorIdx++ % COLORS.length;
            scheduler.addProcess(p);
            table.getItems().add(p);
        });

        // START / PAUSE / RESUME
        startBtn.setOnAction(e -> {
            if (scheduler.getProcesses().isEmpty()) {
                alert("Add at least one process first.");
                return;
            }

            if (timeline != null && timeline.getStatus() == Timeline.Status.RUNNING) {
                timeline.pause();
                startBtn.setText("▶  Resume");
                statusLbl.setText("Paused at t=" + scheduler.getTime());
                return;
            }
            if (timeline != null && timeline.getStatus() == Timeline.Status.PAUSED) {
                timeline.play();
                startBtn.setText("⏸  Pause");
                statusLbl.setText("Running…");
                return;
            }

            int q;
            try   { q = parseInt(quantumFld.getText(), 1, 999, "Quantum"); }
            catch (IllegalArgumentException ex) { alert(ex.getMessage()); return; }

            Scheduler.Algo algo = parseAlgo(algoBox.getValue());
            scheduler.setQuantum(q);
            scheduler.reset();
            ganttPane.getChildren().clear();
            ganttPane.setPrefWidth(0);
            avgWtLabel.setText("Avg Waiting Time: —");
            avgTtLabel.setText("Avg Turnaround Time: —");
            startBtn.setText("⏸  Pause");
            statusLbl.setText("Running…");

            timeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                scheduler.step(algo);
                refreshGantt(ganttPane);
                // NOTE: table auto-refreshes via observable properties — no table.refresh() needed
                statusLbl.setText("t = " + scheduler.getTime());

                if (scheduler.isDone()) {
                    timeline.stop();
                    startBtn.setText("▶  Start");
                    statusLbl.setText("Done  ·  t=" + scheduler.getTime());
                    updateMetrics(avgWtLabel, avgTtLabel);
                }
            }));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
        });

        // BATCH
        batchBtn.setOnAction(e -> {
            if (scheduler.getProcesses().isEmpty()) {
                alert("Add at least one process first.");
                return;
            }
            if (timeline != null) timeline.stop();
            startBtn.setText("▶  Start");

            int q;
            try   { q = parseInt(quantumFld.getText(), 1, 999, "Quantum"); }
            catch (IllegalArgumentException ex) { alert(ex.getMessage()); return; }

            Scheduler.Algo algo = parseAlgo(algoBox.getValue());
            scheduler.setQuantum(q);
            scheduler.reset();
            ganttPane.getChildren().clear();
            ganttPane.setPrefWidth(0);
            avgWtLabel.setText("Avg Waiting Time: —");
            avgTtLabel.setText("Avg Turnaround Time: —");

            while (!scheduler.isDone())
                scheduler.step(algo);

            refreshGantt(ganttPane);
            statusLbl.setText("Done  ·  t=" + scheduler.getTime());
            updateMetrics(avgWtLabel, avgTtLabel);
        });

        // RESET
        resetBtn.setOnAction(e -> {
            if (timeline != null) timeline.stop();
            startBtn.setText("▶  Start");
            statusLbl.setText("");
            scheduler.getProcesses().clear();
            scheduler.reset();
            table.getItems().clear();
            ganttPane.getChildren().clear();
            ganttPane.setPrefWidth(0);
            avgWtLabel.setText("Avg Waiting Time: —");
            avgTtLabel.setText("Avg Turnaround Time: —");
            pidCounter = 1;
            colorIdx   = 0;
        });

        // ── Layout ─────────────────────────────────────────────────────
        VBox root = new VBox(0,
            inputRow,
            controlRow,
            new Separator(),
            table,
            new Separator(),
            ganttTitle,
            ganttScroll,
            metricsBar
        );
        VBox.setMargin(ganttTitle, new Insets(0, 0, 0, 4));

        Scene scene = new Scene(root, 940, 580);
        stage.setScene(scene);
        stage.setTitle("CPU Scheduler Simulator");
        stage.setResizable(true);
        stage.show();
    }

    // ── TextField factory ──────────────────────────────────────────────
    private TextField numField(String defVal, int prefW) {
        TextField tf = new TextField(defVal);
        tf.setPrefWidth(prefW);
        tf.setStyle(
            "-fx-font-size: 13;" +
            "-fx-padding: 4 6;" +
            "-fx-background-radius: 4;" +
            "-fx-border-color: #bbb;" +
            "-fx-border-radius: 4;"
        );
        tf.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.matches("\\d*")) tf.setText(newV.replaceAll("[^\\d]", ""));
        });
        return tf;
    }

    // ── Parse + validate ───────────────────────────────────────────────
    private int parseInt(String text, int min, int max, String fieldName) {
        text = text.trim();
        if (text.isEmpty())
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        int val;
        try { val = Integer.parseInt(text); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a whole number.");
        }
        if (val < min || val > max)
            throw new IllegalArgumentException(
                fieldName + " must be between " + min + " and " + max + ".");
        return val;
    }

    // ── Map ComboBox label → Algo enum ────────────────────────────────
    private Scheduler.Algo parseAlgo(String display) {
        switch (display) {
            case "SJF – Non-Preemptive":        return Scheduler.Algo.SJF_NP;
            case "SJF – Preemptive (SRTF)":     return Scheduler.Algo.SJF_P;
            case "Priority – Non-Preemptive":   return Scheduler.Algo.PRIORITY_NP;
            case "Priority – Preemptive":       return Scheduler.Algo.PRIORITY_P;
            case "Round Robin (RR)":            return Scheduler.Algo.RR;
            default:                             return Scheduler.Algo.FCFS;
        }
    }

    // ── Gantt chart rendering ──────────────────────────────────────────
    private void refreshGantt(Pane ganttPane) {
        ganttPane.getChildren().clear();

        final int BLOCK_W = 50;
        final int BLOCK_H = 44;
        final int Y       = 8;
        final int TICK_Y  = Y + BLOCK_H + 14;

        double maxX = 0;
        List<Scheduler.GanttEntry> entries = scheduler.getGantt();

        for (int i = 0; i < entries.size(); i++) {
            Scheduler.GanttEntry entry = entries.get(i);
            int duration = entry.end - entry.start;
            if (duration <= 0) continue;

            int x = entry.start * BLOCK_W;
            int w = duration    * BLOCK_W;

            Color fill = Color.LIGHTGRAY;
            if (!"idle".equals(entry.pid)) {
                for (Process p : scheduler.getProcesses())
                    if (p.pid.equals(entry.pid)) {
                        fill = COLORS[p.colorIndex % COLORS.length];
                        break;
                    }
            }

            Rectangle rect = new Rectangle(x, Y, w, BLOCK_H);
            rect.setFill(fill);
            rect.setStroke(Color.WHITE);
            rect.setStrokeWidth(2);
            rect.setArcWidth(6);
            rect.setArcHeight(6);

            Text pidText = new Text(entry.pid);
            pidText.setFont(Font.font("System", FontWeight.BOLD, 12));
            pidText.setFill(Color.WHITE);
            pidText.setX(x + w / 2.0 - pidText.getLayoutBounds().getWidth() / 2.0);
            pidText.setY(Y + BLOCK_H / 2.0 + 5);

            Text startTick = new Text(x + 2, TICK_Y, String.valueOf(entry.start));
            startTick.setFont(Font.font(10));
            startTick.setFill(Color.web("#555"));

            ganttPane.getChildren().addAll(rect, pidText, startTick);

            if (i == entries.size() - 1) {
                Text endTick = new Text(x + w - 4, TICK_Y, String.valueOf(entry.end));
                endTick.setFont(Font.font(10));
                endTick.setFill(Color.web("#555"));
                ganttPane.getChildren().add(endTick);
            }

            maxX = Math.max(maxX, x + w + 20);
        }

        if (ganttPane.getPrefWidth() < maxX)
            ganttPane.setPrefWidth(maxX);
    }

    // ── Helpers ────────────────────────────────────────────────────────
    private void updateMetrics(Label wt, Label tt) {
        wt.setText(String.format("Avg Waiting Time: %.2f", scheduler.avgWaiting()));
        tt.setText(String.format("Avg Turnaround Time: %.2f", scheduler.avgTurnaround()));
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<Process, T> tcol(String title, String prop, double w) {
        TableColumn<Process, T> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(w);
        c.setSortable(false);
        return c;
    }

    private void styleBtn(Button btn, String hex) {
        btn.setStyle(
            "-fx-background-color: " + hex + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 13;" +
            "-fx-padding: 5 14;" +
            "-fx-background-radius: 4;"
        );
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited (e -> btn.setOpacity(1.0));
    }

    private Separator sep() {
        return new Separator(javafx.geometry.Orientation.VERTICAL);
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void main(String[] args) { launch(); }
}