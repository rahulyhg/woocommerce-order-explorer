package ch.scbirs.shop.orderexplorer.gui.hotkey;

import ch.scbirs.shop.orderexplorer.util.LogUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static javafx.scene.input.KeyCombination.ModifierValue.DOWN;
import static javafx.scene.input.KeyCombination.ModifierValue.UP;

public class HotkeySettings extends Dialog<Void> {

    private static final Logger LOGGER = LogUtil.get();
    private final Hotkeys hotkeys;
    private KeyCombination last;

    @FXML
    private ListView<Pair<String, KeyCombination>> list;
    @FXML
    private TextField input;
    @FXML
    private Button button;

    public HotkeySettings(ResourceBundle resources, Hotkeys hotkeys) {
        this.hotkeys = hotkeys;
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        setTitle(resources.getString("app.dialog.hotkey.Title"));
        try {
            FXMLLoader loader = new FXMLLoader(HotkeySettings.class.getResource("hotkey_settings.fxml"));
            loader.setResources(resources);
            loader.setController(this);
            Parent p = loader.load();
            getDialogPane().setContent(p);
        } catch (IOException e) {
            LOGGER.error("Can't load hotkey settings dialog fxml", e);
        }

    }

    @FXML
    private void initialize() {
        input.setOnKeyPressed(this::onKeyPressed);
        input.textProperty().addListener((observable, oldValue, newValue) -> {
            LOGGER.info("LISTENER " + last);
        });

        list.setCellFactory(param -> new HotkeyListCell());

        changeItems();
    }

    private void changeItems() {
        list.setItems(FXCollections.observableArrayList(getData()));
    }

    private List<Pair<String, KeyCombination>> getData() {
        List<Pair<String, KeyCombination>> data = new ArrayList<>();
        for (String s : hotkeys.getKeys()) {
            data.add(new ImmutablePair<>(s, hotkeys.getKeyCombination(s)));
        }
        return data;
    }


    private void onKeyPressed(KeyEvent e) {
        if (!e.getCode().isModifierKey()) {
            KeyCodeCombination kcc = new KeyCodeCombination(
                    e.getCode(),
                    e.isShiftDown() ? DOWN : UP,
                    e.isControlDown() ? DOWN : UP,
                    e.isAltDown() ? DOWN : UP,
                    e.isMetaDown() ? DOWN : UP,
                    e.isShortcutDown() ? DOWN : UP
            );
            last = kcc;
            input.setText(kcc.getDisplayText());
        } else {
            StringBuilder b = new StringBuilder();
            if (e.isAltDown() && e.getCode() != KeyCode.ALT) {
                b.append(KeyCode.ALT.getName()).append("+");
            }
            if (e.isControlDown() && e.getCode() != KeyCode.CONTROL) {
                b.append(KeyCode.CONTROL.getName()).append("+");
            }
            if (e.isMetaDown() && e.getCode() != KeyCode.META) {
                b.append(KeyCode.META.getName()).append("+");
            }
            if (e.isShiftDown() && e.getCode() != KeyCode.SHIFT) {
                b.append(KeyCode.SHIFT.getName()).append("+");
            }
            b.append(e.getCode().getName()).append("+");
            last = null;
            input.setText(b.toString());
        }
    }

    @FXML
    private void onButtonSet() {
        Pair<String, KeyCombination> item = list.getSelectionModel().getSelectedItem();
        KeyCombination kc = last;

        hotkeys.changeBinding(item.getLeft(), kc);
        changeItems();
    }
}
