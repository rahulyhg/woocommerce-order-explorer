package ch.scbirs.shop.orderexplorer.gui;

import ch.scbirs.shop.orderexplorer.OrderExplorer;
import ch.scbirs.shop.orderexplorer.backup.BackupProvider;
import ch.scbirs.shop.orderexplorer.gui.filter.*;
import ch.scbirs.shop.orderexplorer.gui.hotkey.HotkeySettings;
import ch.scbirs.shop.orderexplorer.gui.hotkey.Hotkeys;
import ch.scbirs.shop.orderexplorer.gui.report.MoneyReport;
import ch.scbirs.shop.orderexplorer.gui.report.OrderReport;
import ch.scbirs.shop.orderexplorer.gui.report.OverviewReport;
import ch.scbirs.shop.orderexplorer.gui.report.ReportScreen;
import ch.scbirs.shop.orderexplorer.gui.util.AlertUtil;
import ch.scbirs.shop.orderexplorer.gui.util.DirtyObjectProperty;
import ch.scbirs.shop.orderexplorer.gui.util.ExceptionAlert;
import ch.scbirs.shop.orderexplorer.gui.util.TaskAlert;
import ch.scbirs.shop.orderexplorer.model.Data;
import ch.scbirs.shop.orderexplorer.model.local.UserDataCleaner;
import ch.scbirs.shop.orderexplorer.model.local.UserSettings;
import ch.scbirs.shop.orderexplorer.model.remote.Order;
import ch.scbirs.shop.orderexplorer.report.FullReport;
import ch.scbirs.shop.orderexplorer.util.BindingUtil;
import ch.scbirs.shop.orderexplorer.util.LogUtil;
import ch.scbirs.shop.orderexplorer.version.GithubReleaseQuery;
import ch.scbirs.shop.orderexplorer.version.VersionUtil;
import ch.scbirs.shop.orderexplorer.web.CheckConnectionTask;
import ch.scbirs.shop.orderexplorer.web.WebRequesterTask;
import com.github.zafarkhaja.semver.Version;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.ResourceBundle;

public class GuiController {

    private static final Logger LOGGER = LogUtil.get();
    private static final String HOTKEY_RELOAD = "data.Reload";
    private static final String HOTKEY_REPORT_ORDER = "report.export.OrderReport";
    private static final String HOTKEY_REPORT_OVERVIEW = "report.export.OverviewReport";
    private static final String HOTKEY_REPORT_FULL = "report.export.FullReport";
    private static final String HOTKEY_SAVE = "data.Save";

    private final DirtyObjectProperty<Data> data = new DirtyObjectProperty<>();

    private final BooleanBinding saved = data.dirtyProperty().not();

    private Filter filter;

    private Stage primaryStage;
    private OrderPanelController orderPanel;
    private HostServices hostServices;
    @FXML
    private ResourceBundle resources;

    @FXML
    private TextField search;
    @FXML
    private Menu recentBackup;
    @FXML
    private ListView<Order> list;
    @FXML
    private AnchorPane detailPane;

    public GuiController() {
        data.setValue(new Data());
        data.addListener(this::onNewData);
    }

    @SuppressWarnings("unused")
    private void onNewData(ObservableValue<? extends Data> o, Data oldData, Data data) {
        LOGGER.info("Data value has changed ");
        int idx = list.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            idx = 0;
        }
        if (filter == null) {
            filter = new Filter(data.getOrders());
        } else {
            filter = filter.clone(data.getOrders());
        }

        list.setItems(filter.getFilteredOutput());
        if (list.getItems().size() > idx) {
            list.getSelectionModel().select(idx);
        }
    }

    public void setStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            onExit();
        });
    }

    @FXML
    public void initialize() throws IOException {

        ReportScreen.setResources(resources);

        list.setCellFactory(param -> new OrderListCell(data));

        list.getSelectionModel().selectedItemProperty().addListener((o, oldv, newv) -> orderPanel.setCurrentOrder(newv));

        FXMLLoader loader = new FXMLLoader(GuiController.class.getResource("order_panel.fxml"));
        loader.setResources(resources);
        Parent panel = loader.load();
        orderPanel = loader.getController();
        orderPanel.setData(this.data);
        detailPane.getChildren().add(panel);

        if (Files.exists(OrderExplorer.SETTINGS_FILE)) {
            try {
                data.setValue(Data.fromJsonFile(OrderExplorer.SETTINGS_FILE));
                data.resetDirty();
            } catch (Exception e) {
                LOGGER.warn("Failed to open json file on startup", e);
            }
        }

        Hotkeys hotkeys = Hotkeys.getInstance();
        hotkeys.keymap(HOTKEY_RELOAD, KeyCode.F5, this::onReload);
        hotkeys.keymap(HOTKEY_REPORT_ORDER, KeyCode.F1, this::generateReportOrder);
        hotkeys.keymap(HOTKEY_REPORT_OVERVIEW, KeyCode.F2, this::generateReportOverview);
        hotkeys.keymap(HOTKEY_REPORT_FULL, KeyCode.F3, this::generateReportFull);
        hotkeys.keymap(HOTKEY_SAVE, new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::onSave);

        ObservableList<String> backups = BackupProvider.backups();
        ObservableList<MenuItem> items = recentBackup.getItems();
        EventHandler<ActionEvent> actionEventHandle = event -> {
            String filename = ((MenuItem) event.getSource()).getText();
            ExceptionAlert.doTry(() -> data.set(BackupProvider.loadBackup(filename))
            );
        };
        BindingUtil.mapContent(items, backups, MenuItem::new,
                m -> m.setOnAction(actionEventHandle), m -> m.setOnAction(null)
        );

        if (data.get().getUserData().getUserSettings().isEmpty()) {
            Platform.runLater(this::initNewData);
        }

        versionCheck();
    }

    private void versionCheck() {
        Version currversion = VersionUtil.getVersion();

        if (currversion == VersionUtil.DEV_VERSION) {
            LOGGER.info("Running dev version. Disabling version check.");
            return;
        }

        GithubReleaseQuery grq = new GithubReleaseQuery("Tiim", "woocommerce-order-explorer");
        grq.isNewerVersionAvailable(currversion).thenAccept(b -> Platform.runLater(() -> {
            if (b.getLeft()) {
                AlertUtil.showInfo(String.format("New version %s available!", b.getRight().getVersion()), primaryStage);
            }
        }));

    }

    private void initNewData() {
        LOGGER.info("Initializing new data on startup");
        onReload();
    }

    @FXML
    private void onOpen() throws IOException {
        if (Files.exists(OrderExplorer.SETTINGS_FILE)) {
            Data d = Data.fromJsonFile(OrderExplorer.SETTINGS_FILE);
            data.setValue(d);
            data.resetDirty();
        } else {
            AlertUtil.showError(resources.getString("app.open.error.NoFile"), primaryStage);
        }
    }

    @FXML
    private void onSave() throws IOException {
        if (data.get() != null) {
            try {
                BackupProvider.nextBackup(data.get());
            } catch (IOException e) {
                LOGGER.warn("Can't make backup", e);
            }
            Data.toJsonFile(OrderExplorer.SETTINGS_FILE, data.get());
            data.resetDirty();
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(resources.getString("app.dialog.save.NoData"));
            alert.show();
        }
    }

    @FXML
    private void onSettingsDialog() {
        Data old = data.get();
        SettingsDialog sd = new SettingsDialog(old.getUserData().getUserSettings(), resources, primaryStage);
        Optional<UserSettings> newsettings = sd.showAndWait();
        if (newsettings.isPresent()) {
            Data d = data.get().withUserData(data.get().getUserData().withUserSettings(newsettings.get()));

            Task<Boolean> task = new CheckConnectionTask(newsettings.get());
            TaskAlert<Boolean> alert = new TaskAlert<>(task, resources.getString("app.dialog.conncheck.Title"),
                    resources.getString("app.dialog.conncheck.Header"), primaryStage);

            task.setOnSucceeded(e -> {
                alert.close();
                if (!task.getValue()) {
                    AlertUtil.showError(resources.getString("app.dialog.conncheck.Error"), primaryStage);
                } else {
                    data.set(d);
                }
            });

            Thread t = new Thread(task);
            t.start();
            alert.showAndWait();
        }
    }

    @FXML
    private void onCheckConnection() {
        if (data.get().getUserData().getUserSettings().isEmpty()) {
            AlertUtil.showError(resources.getString("app.dialog.conncheck.NoSettings"), primaryStage);
            return;
        }
        Task<Boolean> task = new CheckConnectionTask(data.get().getUserData().getUserSettings());
        TaskAlert<Boolean> alert = new TaskAlert<>(task, resources.getString("app.dialog.conncheck.Title"),
                resources.getString("app.dialog.conncheck.Header"), primaryStage);

        task.setOnSucceeded(e -> {
            alert.close();
            if (!task.getValue()) {
                AlertUtil.showError(resources.getString("app.dialog.conncheck.Error"), primaryStage);
            } else {
                AlertUtil.showInfo(resources.getString("app.dialog.conncheck.Success"), primaryStage);
            }
        });

        Thread t = new Thread(task);
        t.start();
        alert.show();
    }

    @FXML
    private void onReload() {

        if (data.get() != null) {
            try {
                BackupProvider.nextBackup(data.get());
            } catch (IOException e) {
                LOGGER.warn("Can't make backup", e);
            }
        }

        if (data.get().getUserData().getUserSettings().isEmpty()) {
            onSettingsDialog();
        }

        if (data.get().getUserData().getUserSettings().isEmpty()) {
            AlertUtil.showWarning(resources.getString("app.reload.NoSettings"), primaryStage);
            return;
        }

        Task<Data> task = new WebRequesterTask(data.get());

        Alert alert = new TaskAlert<>(task, resources.getString("app.dialog.loading.Title"),
                resources.getString("app.dialog.loading.Header"), primaryStage);

        task.setOnSucceeded(event -> {
            alert.close();
            data.setValue(task.getValue());
            ExceptionAlert.doTry(() -> Data.toJsonFile(OrderExplorer.SETTINGS_FILE, data.get()));
        });

        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();

        alert.show();
    }

    @FXML
    private void onBackup() {
        if (data.get() != null) {
            ExceptionAlert.doTry(() -> BackupProvider.nextBackup(data.get()));
        } else {
            AlertUtil.showWarning(resources.getString("app.dialog.backup.NoData"), primaryStage);
        }
    }

    @FXML
    private void onExit() {
        if (!saved.get()) {
            ButtonType save = new ButtonType(resources.getString("buttontype.Save"), ButtonBar.ButtonData.APPLY);
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    null,
                    save,
                    ButtonType.CANCEL, ButtonType.NO
            );
            AlertUtil.setDefaultButton(alert, save);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.setHeaderText(resources.getString("app.dialog.close.Message"));
            alert.initOwner(primaryStage);

            Optional<ButtonType> btn = alert.showAndWait();
            if (btn.isPresent() && btn.get() == ButtonType.NO) {
                LOGGER.info("Close without saving");
                System.exit(0);
            } else if (btn.isPresent() && btn.get() == save) {
                LOGGER.info("Close with saving");
                ExceptionAlert.doTry(this::onSave);
                System.exit(0);
            }
            LOGGER.info("Abort closing");
        } else {
            LOGGER.info("Close - no save necessary");
            System.exit(0);
        }
    }

    @FXML
    private void handleAboutAction() {
        AboutDialog ad = new AboutDialog(resources, hostServices);
        ad.show();
    }

    @FXML
    private void onSearch() {
        String s = search.getText().toLowerCase().trim();
        if (s.isEmpty()) {
            filter.removeFilter("search");
        }
        filter.addFilter("search", new SearchFilter(s));
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
        orderPanel.setHostServices(hostServices);
    }

    @FXML
    private void generateReportOrder() throws IOException {
        ReportScreen rs = new ReportScreen(new OrderReport(data.get()));
        rs.show();
    }

    @FXML
    private void generateReportOverview() throws IOException {
        ReportScreen rs = new ReportScreen(new OverviewReport(data.get()));
        rs.show();
    }

    @FXML
    private void generateReportMoney() throws IOException {
        ReportScreen rs = new ReportScreen(new MoneyReport(data.get()));
        rs.show();
    }

    @FXML
    private void generateReportFull() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(resources.getString("app.dialog.report.full.filechooser.Title"));
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel File", "*.xls"));
        File selectedFile = chooser.showSaveDialog(primaryStage);
        if (selectedFile != null) {
            ExceptionAlert.doTry(() -> new FullReport(data.get()).save(selectedFile.toPath()));
        }
    }

    @FXML
    private void onHotkeyDialog() {
        HotkeySettings hs = new HotkeySettings(primaryStage, resources, Hotkeys.getInstance());
        hs.show();
    }

    @FXML
    private void onFilterButton(ActionEvent actionEvent) {
        ToggleButton btn = (ToggleButton) actionEvent.getSource();
        switch (btn.getId()) {
            case "filter_done":
                filter.toggleFilter("status_done", new DoneFilter(data));
                break;
            case "filter_paid":
                filter.toggleFilter("status_paid", new PaidFilter(data));
                break;
            case "filter_in_stock":
                filter.toggleFilter("status_in_stock", new InStockFilter(data));
                break;
        }
    }

    public void setUserSettings(UserSettings args) {
        data.set(data.get().withUserData(data.get().getUserData().withUserSettings(args)));
    }

    public BooleanBinding savedProperty() {
        return saved;
    }

    public void onClean(ActionEvent actionEvent) {
        data.set(new UserDataCleaner().clean(data.get()));
    }
}
