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
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
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
    private final ChangeListener<Status> changedListener = this::changed;
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
    private ComboBox<Status> status;

    public ProductListCell(ObjectProperty<Data> data) {
        this.data = data;
    }

    @FXML
    private void initialize() {
        status.setItems(FXCollections.observableArrayList(Status.values()));
    }

    @Override
    protected void updateItem(Product item, boolean empty) {
        super.updateItem(item, empty);
        currentItem = item;

        if (empty || item == null) {
            setGraphic(null);
            DataUtil.setPseudoClass(this, null);
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
            status.getSelectionModel().selectedItemProperty().removeListener(changedListener);

            Path imgPath = OrderExplorer.FOLDER.resolve(data.get().getImage(item));

            try {
                Image value = new Image(Files.newInputStream(imgPath), 200, 200, true, true);
                img.setImage(value);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e);
            }
            name.setText(item.getName());
            price.setText("CHF " + String.valueOf(item.getPrice()));
            quantity.setText(String.valueOf(item.getQuantity()) + "x");
            sku.setText(item.getSku());

            ProductData productData = data.get().getUserData().getProductData(item);
            Status status = productData.getStatus();
            this.status.getSelectionModel().select(status);
            DataUtil.setPseudoClass(this, status);

            Map<String, String> meta = item.getMeta();
            if (Env.getInstance().debug) {
                meta = new HashMap<>(meta);
                meta.put("id", String.valueOf(item.getId()));
            }

            this.meta.setText(Util.formatMap(meta).toUpperCase());

            this.status.getSelectionModel().selectedItemProperty().addListener(changedListener);
            setGraphic(root);
        }

    }

    private void changed(ObservableValue<? extends Status> o, Status old, Status n) {
        Data oldData = this.data.get();
        UserData oldUserData = oldData.getUserData();
        UserSettings settings = oldUserData.getUserSettings();
        Map<Integer, ProductData> oldProductDataMap = oldUserData.getProductData();

        ProductData newProductData = new ProductData(n);

        Map<Integer, ProductData> newProductDataMap = new HashMap<>(oldProductDataMap);
        newProductDataMap.put(currentItem.getId(), newProductData);


        UserData ud = new UserData(newProductDataMap, settings);
        Data d = new Data(oldData.getOrders(), oldData.getImages(), ud);

        data.set(d);
    }
}
