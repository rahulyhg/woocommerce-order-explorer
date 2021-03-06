package ch.scbirs.shop.orderexplorer.gui;

import ch.scbirs.shop.orderexplorer.Env;
import ch.scbirs.shop.orderexplorer.OrderExplorer;
import ch.scbirs.shop.orderexplorer.model.Data;
import ch.scbirs.shop.orderexplorer.model.local.ProductData;
import ch.scbirs.shop.orderexplorer.model.local.Status;
import ch.scbirs.shop.orderexplorer.model.local.UserData;
import ch.scbirs.shop.orderexplorer.model.local.UserSettings;
import ch.scbirs.shop.orderexplorer.model.remote.Product;
import ch.scbirs.shop.orderexplorer.util.DataUtil;
import ch.scbirs.shop.orderexplorer.util.LogUtil;
import ch.scbirs.shop.orderexplorer.util.Util;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProductListCell extends ListCell<Product> {
    private static final Logger LOGGER = LogUtil.get();
    private final ObjectProperty<Data> data;
    @FXML
    private CheckBox isPaid;
    private FXMLLoader loader;


    private Parent root;

    private Product currentItem;

    @FXML
    private ImageView img;
    @FXML
    private Label name;
    @FXML
    private Label price;
    @FXML
    private Label quantity;
    @FXML
    private Label sku;
    @FXML
    private Label meta;
    @FXML
    private CheckBox isInStock;
    @FXML
    private CheckBox isDone;
    private final ChangeListener<Boolean> changedListener = this::changed;

    public ProductListCell(ObjectProperty<Data> data) {
        this.data = data;
    }

    @FXML
    private void initialize() {

    }

    @Override
    protected void updateItem(Product item, boolean empty) {
        super.updateItem(item, empty);
        currentItem = item;

        if (empty || item == null) {
            setGraphic(null);
            DataUtil.setPseudoClass(this, (Status) null);
        } else {
            if (loader == null) {
                loader = new FXMLLoader(ProductListCell.class.getResource("product_list_item.fxml"));
                loader.setController(this);

                try {
                    root = loader.load();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Path imgPath = OrderExplorer.FOLDER.resolve(data.get().getImage(item));

            try {
                Image value = new Image(Files.newInputStream(imgPath), 200, 200, true, true);
                img.setImage(value);
            } catch (IOException e) {
                LOGGER.warn("Can't read image");
            }
            name.setText(item.getName());
            price.setText("CHF " + String.valueOf(item.getPrice()));
            quantity.setText(String.valueOf(item.getQuantity()) + "x");
            sku.setText(item.getSku());


            ProductData productData = data.get().getUserData().getProductData(item);
            Status status = productData.getStatus();
            updateStatus(status);

            DataUtil.setPseudoClass(this, status);

            Map<String, String> meta = item.getMeta();
            if (Env.getInstance().debug) {
                meta = new HashMap<>(meta);
                meta.put("id", String.valueOf(item.getId()));
            }

            this.meta.setText(Util.formatMap(meta).toUpperCase());

            setGraphic(root);
        }

    }

    private void updateStatus(Status status) {

        isPaid.selectedProperty().removeListener(changedListener);
        isInStock.selectedProperty().removeListener(changedListener);
        isDone.selectedProperty().removeListener(changedListener);

        isPaid.setSelected(status.isPaid());
        isInStock.setSelected(status.isInStock());
        isDone.setSelected(status.isDone());

        isPaid.selectedProperty().addListener(changedListener);
        isInStock.selectedProperty().addListener(changedListener);
        isDone.selectedProperty().addListener(changedListener);
    }

    @SuppressWarnings("unused")
    private void changed(ObservableValue<? extends Boolean> o, Boolean old, Boolean n) {
        Data oldData = this.data.get();
        UserData oldUserData = oldData.getUserData();
        UserSettings settings = oldUserData.getUserSettings();
        Map<Integer, ProductData> oldProductDataMap = oldUserData.getProductData();

        Status status = new Status(isInStock.isSelected(), isPaid.isSelected(), isDone.isSelected());
        ProductData newProductData = new ProductData(
                status
        );

        updateStatus(status);

        Map<Integer, ProductData> newProductDataMap = new HashMap<>(oldProductDataMap);
        newProductDataMap.put(currentItem.getId(), newProductData);


        UserData ud = new UserData(newProductDataMap, settings);
        Data d = oldData.withUserData(oldData.getUserData().withProductData(newProductDataMap));

        data.set(d);
    }
}
