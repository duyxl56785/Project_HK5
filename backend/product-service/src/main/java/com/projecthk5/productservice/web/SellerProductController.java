package com.projecthk5.productservice.web;

import com.projecthk5.productservice.image.ImageStorage;
import com.projecthk5.productservice.image.ProductImageValidator;
import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductOwnerQuery;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.http.ProductManagementHandler;
import com.projecthk5.productservice.product.http.ProductManagementResponse;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import com.projecthk5.security.Actor;
import com.projecthk5.security.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** SELLER management of Products in the verified current Store only (store never comes from the client). */
@RestController
@RequestMapping("/api/seller/products")
public class SellerProductController {
    private final ProductManagementHandler handler;
    private final ProductApplicationService application;
    private final SellerStoreClient stores;
    private final DependencyClients dependencies;
    private final DetailsRepository details;
    private final ImageRepository images;
    private final ImageStorage storage;
    private final ProductImageValidator validator;

    public SellerProductController(ProductManagementHandler handler, ProductApplicationService application,
                                   SellerStoreClient stores, DependencyClients dependencies, DetailsRepository details,
                                   ImageRepository images, ImageStorage storage, ProductImageValidator validator) {
        this.handler = handler;
        this.application = application;
        this.stores = stores;
        this.dependencies = dependencies;
        this.details = details;
        this.images = images;
        this.storage = storage;
        this.validator = validator;
    }

    public record CreateRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 128) String sku, @NotBlank @Size(max = 255) String name,
                                @NotNull BigDecimal unitPrice, @Size(max = 5000) String description,
                                @PositiveOrZero @Max(1_000_000) Long initialStock) {}
    public record EditRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 255) String name, @NotNull BigDecimal unitPrice,
                              @Size(max = 5000) String description, @NotBlank String expectedVersion) {}
    public record VersionRequest(@NotBlank String expectedVersion) {}
    public record StockRequest(@NotNull @PositiveOrZero @Max(1_000_000) Long available) {}
    public record ImageView(UUID id, int displayOrder, String url) {
        static ImageView of(ProductExtras.Image i) { return new ImageView(i.getId(), i.getDisplayOrder(), "/api/catalog/images/" + i.getId()); }
    }
    public record SellerProduct(ProductManagementResponse product, String description, List<ImageView> images,
                                Long available, Long reserved) {}

    @GetMapping
    public ProductManagementHandler.OwnedPage list(@RequestParam(required = false) String term,
                                                   @RequestParam(required = false) ProductStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return handler.listOwned(new ProductOwnerQuery(term, status, page, size));
    }

    @GetMapping("/{id}")
    public SellerProduct get(@PathVariable UUID id) {
        return view(handler.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SellerProduct create(@Valid @RequestBody CreateRequest req) {
        String actor = Actor.current().id();
        UUID storeId = stores.currentManagementStore(actor);
        StoredProduct created = application.create(actor, storeId, req.categoryId(), req.sku().strip(), req.name(),
                req.unitPrice(), "VND");
        details.save(new ProductExtras.Details(created.listing().id(), req.description() == null ? "" : req.description().strip()));
        if (req.initialStock() != null && req.initialStock() > 0)
            dependencies.setAvailable(created.listing().sku(), req.initialStock(), "stock-init-" + created.listing().id());
        return view(ProductManagementResponse.from(created));
    }

    @PutMapping("/{id}")
    @Transactional
    public SellerProduct edit(@PathVariable UUID id, @Valid @RequestBody EditRequest req) {
        StoredProduct edited = application.edit(Actor.current().id(), id, version(req.expectedVersion()), req.categoryId(),
                req.name(), req.unitPrice(), "VND");
        if (req.description() != null) {
            details.findById(id).ifPresentOrElse(d -> d.setDescription(req.description().strip()),
                    () -> details.save(new ProductExtras.Details(id, req.description().strip())));
        }
        return view(ProductManagementResponse.from(edited));
    }

    @PostMapping("/{id}/submit")
    public SellerProduct submit(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        return view(ProductManagementResponse.from(application.submit(Actor.current().id(), id, version(req.expectedVersion()))));
    }

    @PostMapping("/{id}/deactivate")
    public SellerProduct deactivate(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        return view(ProductManagementResponse.from(application.deactivate(Actor.current().id(), id, version(req.expectedVersion()))));
    }

    @PutMapping("/{id}/stock")
    public SellerProduct stock(@PathVariable UUID id, @Valid @RequestBody StockRequest req) {
        ProductManagementResponse product = handler.findById(id); // ownership check first
        dependencies.setAvailable(product.sku(), req.available(), "stock-" + id + "-" + UUID.randomUUID());
        return view(product);
    }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    @Transactional
    public SellerProduct upload(@PathVariable UUID id, @RequestParam("files") List<MultipartFile> files) throws IOException {
        ProductManagementResponse product = handler.findById(id);
        List<ProductExtras.Image> existing = images.findByProductIdOrderByDisplayOrder(id);
        List<ProductImageValidator.ImageInput> inputs = new ArrayList<>();
        for (MultipartFile f : files) inputs.add(new ProductImageValidator.ImageInput(f.getOriginalFilename(), f.getContentType(), f.getInputStream()));
        var validated = validator.validateBatch(inputs, existing.size());
        for (var image : validated) {
            int slot = firstFreeSlot(id);
            var stored = storage.store(image);
            images.saveAndFlush(new ProductExtras.Image(id, stored.key(), image.format().contentType(),
                    stored.width(), stored.height(), stored.size(), slot));
        }
        return view(product);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Transactional
    public SellerProduct deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        ProductManagementResponse product = handler.findById(id);
        ProductExtras.Image image = images.findById(imageId).filter(i -> i.getProductId().equals(id))
                .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "Image not found"));
        images.delete(image);
        return view(product);
    }

    private int firstFreeSlot(UUID productId) {
        var used = images.findByProductIdOrderByDisplayOrder(productId).stream().map(ProductExtras.Image::getDisplayOrder).toList();
        for (int s = 1; s <= ProductImageValidator.MAX_IMAGES; s++) if (!used.contains(s)) return s;
        throw ApiException.conflict("IMAGE_COUNT_EXCEEDED", "A product can have at most five images");
    }

    private SellerProduct view(ProductManagementResponse product) {
        DependencyClients.Balance balance = dependencies.balance(product.sku());
        return new SellerProduct(product, details.findById(product.id()).map(ProductExtras.Details::getDescription).orElse(""),
                images.findByProductIdOrderByDisplayOrder(product.id()).stream().map(ImageView::of).toList(),
                balance == null ? null : balance.availableQuantity(), balance == null ? null : balance.reservedQuantity());
    }

    static long version(String text) {
        if (text == null || !text.matches("0|[1-9][0-9]{0,17}")) throw ApiException.badRequest("VALIDATION_FAILED", "Invalid expectedVersion");
        return Long.parseLong(text);
    }
}
