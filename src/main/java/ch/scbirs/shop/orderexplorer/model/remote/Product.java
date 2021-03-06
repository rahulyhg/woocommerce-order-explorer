package ch.scbirs.shop.orderexplorer.model.remote;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Product {

    private final int id;
    private final int quantity;
    @Nonnull
    private final String name;
    @Nonnull
    private final Map<String, String> meta;
    private final double price;
    @Nonnull
    private final String sku;

    private final int productId;
    private final int variationId;

    private Product() {
        id = 0;
        quantity = 0;
        name = "";
        meta = Collections.emptyMap();
        price = 0;
        sku = "";
        productId = 0;
        variationId = 0;
    }

    public Product(int id, int quantity, @Nonnull String name, Map<String, String> meta, double price, @Nonnull String sku,
                   int productId, int variationId) {
        this.id = id;
        this.quantity = quantity;
        this.name = name;
        this.meta = Collections.unmodifiableMap(new HashMap<>(meta));
        this.price = price;
        this.sku = sku;
        this.productId = productId;
        this.variationId = variationId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("quantity", quantity)
                .add("name", name)
                .add("meta", meta)
                .add("price", price)
                .add("sku", sku)
                .add("productId", productId)
                .add("variationId", variationId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id == product.id &&
                quantity == product.quantity &&
                Double.compare(product.price, price) == 0 &&
                productId == product.productId &&
                variationId == product.variationId &&
                Objects.equal(name, product.name) &&
                Objects.equal(meta, product.meta) &&
                Objects.equal(sku, product.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, quantity, name, meta, price, sku, productId, variationId);
    }

    public int getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public Map<String, String> getMeta() {
        return meta;
    }

    public double getPrice() {
        return price;
    }

    @Nonnull
    public String getSku() {
        return sku;
    }

    public int getProductId() {
        return productId;
    }

    public int getVariationId() {
        return variationId;
    }
}
