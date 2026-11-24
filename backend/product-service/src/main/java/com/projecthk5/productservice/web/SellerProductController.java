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
import java.util.function.BiFunction;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final VariantRepository variants;
    private final VariantGroups groups;

    public SellerProductController(ProductManagementHandler handler, ProductApplicationService application,
                                   SellerStoreClient stores, DependencyClients dependencies, DetailsRepository details,
                                   ImageRepository images, ImageStorage storage, ProductImageValidator validator,
                                   VariantRepository variants, VariantGroups groups) {
        this.handler = handler;
        this.application = application;
        this.stores = stores;
        this.dependencies = dependencies;
        this.details = details;
        this.images = images;
        this.storage = storage;
        this.validator = validator;
        this.variants = variants;
        this.groups = groups;
    }

    /** Without variants, sku and unitPrice are required; with variants each variant carries its own. */
    public record CreateRequest(@NotNull UUID categoryId, @Size(max = 128) String sku, @NotBlank @Size(max = 255) String name,
                                BigDecimal unitPrice, @Size(max = 5000) String description,
                                @PositiveOrZero @Max(1_000_000) Long initialStock,
                                @Valid @Size(max = ProductVariantRules.MAX_VARIANTS) List<VariantInput> variants) {}
    public record VariantInput(@Size(max = ProductVariantRules.MAX_SIZE) String size,
                               @Size(max = ProductVariantRules.MAX_COLOR) String color,
                               @NotBlank @Size(max = 128) String sku, @NotNull BigDecimal unitPrice,
                               @PositiveOrZero @Max(1_000_000) Long initialStock) {}
    public record VariantView(UUID groupId, int position, String size, String color) {}
    public record VariantRow(UUID id, String sku, String name, String size, String color, String unitPrice,
                             ProductStatus status, String version, Long available) {}
    public record EditRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 255) String name, @NotNull BigDecimal unitPrice,
                              @Size(max = 5000) String description, @NotBlank String expectedVersion) {}
    public record VersionRequest(@NotBlank String expectedVersion) {}
    public record StockRequest(@NotNull @PositiveOrZero @Max(1_000_000) Long available) {}
    public record ImageView(UUID id, int displayOrder, String url) {
        static ImageView of(ProductExtras.Image i) { return new ImageView(i.getId(), i.getDisplayOrder(), "/api/catalog/images/" + i.getId()); }
    }
    /** variant is null and variants empty for a Product without size/color; variants lists the whole group. */
    public record SellerProduct(ProductManagementResponse product, String description, List<ImageView> images,
                                Long available, Long reserved, VariantView variant, List<VariantRow> variants) {}

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
        if (req.variants() != null && !req.variants().isEmpty()) return view(ProductManagementResponse.from(createGroup(actor, storeId, req)));
        if (req.sku() == null || req.sku().isBlank() || req.unitPrice() == null)
            throw ApiException.badRequest("VALIDATION_FAILED", "SKU and unit price are required");
        StoredProduct created = application.create(actor, storeId, req.categoryId(), req.sku().strip(), req.name(),
                req.unitPrice(), "VND");
        details.saveAndFlush(new ProductExtras.Details(created.listing().id(), req.description() == null ? "" : req.description().strip()));
        InitialStock.apply(dependencies, List.of(stockLine(created, req.initialStock())));
        return view(ProductManagementResponse.from(created));
    }

    private static InitialStock.Line stockLine(StoredProduct product, Long initial) {
        return new InitialStock.Line(product.listing().sku(), initial == null ? 0 : initial, "stock-init-" + product.listing().id());
    }

    /**
     * One Product per size/color option, all in this transaction: a refused variant leaves no row of the group.
     * Stock is set only after every row is stored, and InitialStock takes it back if the transaction rolls back.
     */
    private StoredProduct createGroup(String actor, UUID storeId, CreateRequest req) {
        List<ProductVariantRules.Option> options = req.variants().stream()
                .map(v -> ProductVariantRules.option(v.size(), v.color())).toList();
        ProductVariantRules.requireGroup(options);
        List<StoredProduct> created = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            VariantInput input = req.variants().get(i);
            StoredProduct product = application.create(actor, storeId, req.categoryId(), input.sku().strip(),
                    ProductVariantRules.variantName(req.name(), options.get(i)), input.unitPrice(), "VND");
            UUID groupId = created.isEmpty() ? product.listing().id() : created.get(0).listing().id();
            variants.saveAndFlush(new ProductExtras.Variant(product.listing().id(), groupId, i + 1,
                    options.get(i).size(), options.get(i).color()));
            created.add(product);
        }
        StoredProduct lead = created.get(0);
        details.saveAndFlush(new ProductExtras.Details(lead.listing().id(), req.description() == null ? "" : req.description().strip()));
        List<InitialStock.Line> stock = new ArrayList<>();
        for (int i = 0; i < created.size(); i++) stock.add(stockLine(created.get(i), req.variants().get(i).initialStock()));
        InitialStock.apply(dependencies, stock);
        return lead;
    }

    @PutMapping("/{id}")
    @Transactional
    public SellerProduct edit(@PathVariable UUID id, @Valid @RequestBody EditRequest req) {
        String actor = Actor.current().id();
        ProductExtras.Variant variant = groups.of(id).orElse(null);
        if (variant != null && !variant.isLead()) return view(ProductManagementResponse.from(editVariant(actor, variant, req)));
        boolean descriptionChanged = req.description() != null && !req.description().strip().equals(
                details.findById(id).map(ProductExtras.Details::getDescription).orElse(""));
        String name = variant == null ? req.name() : ProductVariantRules.variantName(req.name(), variant.option());
        StoredProduct edited = application.edit(actor, id, version(req.expectedVersion()), req.categoryId(),
                name, req.unitPrice(), "VND", descriptionChanged);
        if (req.description() != null) {
            details.findById(id).ifPresentOrElse(d -> d.setDescription(req.description().strip()),
                    () -> details.save(new ProductExtras.Details(id, req.description().strip())));
        }
        if (variant != null) renameGroup(actor, variant, req);
        return view(ProductManagementResponse.from(edited));
    }

    /** The lead's name and category are the group's; each other variant keeps its own price. */
    private void renameGroup(String actor, ProductExtras.Variant lead, EditRequest req) {
        for (ProductExtras.Variant member : groups.group(lead.getGroupId())) {
            if (member.isLead()) continue;
            StoredProduct current = groups.current(member.getProductId());
            String name = ProductVariantRules.variantName(req.name(), member.option());
            if (name.equals(current.listing().name()) && req.categoryId().equals(current.listing().categoryId())) continue;
            application.edit(actor, member.getProductId(), current.version(), req.categoryId(), name,
                    current.listing().unitPrice(), "VND", false);
        }
    }

    /** A non-lead variant changes only its price here; name, category and description belong to the lead. */
    private StoredProduct editVariant(String actor, ProductExtras.Variant variant, EditRequest req) {
        var current = application.findById(actor, variant.getProductId()).listing();
        String base = ProductVariantRules.baseName(current.name(), variant.option());
        if (!base.equals(req.name().strip()) || !current.categoryId().equals(req.categoryId()))
            throw ApiException.conflict("VARIANT_GROUP_FIELD", "Change the name and category on the first variant");
        return application.edit(actor, variant.getProductId(), version(req.expectedVersion()), current.categoryId(),
                current.name(), req.unitPrice(), "VND", false);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public SellerProduct submit(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        String actor = Actor.current().id();
        return view(step(id, req, (product, version) -> application.submit(actor, product, version)));
    }

    @PostMapping("/{id}/activate")
    @Transactional
    public SellerProduct activate(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        String actor = Actor.current().id();
        return view(step(id, req, (product, version) -> application.activate(actor, product, version)));
    }

    @PostMapping("/{id}/deactivate")
    @Transactional
    public SellerProduct deactivate(@PathVariable UUID id, @Valid @RequestBody VersionRequest req) {
        String actor = Actor.current().id();
        return view(step(id, req, (product, version) -> application.deactivate(actor, product, version)));
    }

    /** A step on the lead also moves the variants that were in the lead's state; a variant on its own moves alone. */
    private ProductManagementResponse step(UUID id, VersionRequest req, BiFunction<UUID, Long, StoredProduct> step) {
        ProductStatus before = groups.statusOf(id);
        StoredProduct changed = step.apply(id, version(req.expectedVersion()));
        groups.follow(id, before, step);
        return ProductManagementResponse.from(changed);
    }

    @PutMapping("/{id}/stock")
    public SellerProduct stock(@PathVariable UUID id, @Valid @RequestBody StockRequest req) {
        ProductManagementResponse product = handler.findById(id); // ownership check first
        dependencies.setAvailable(product.sku(), req.available(), "stock-" + id + "-" + UUID.randomUUID());
        return view(product);
    }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    @Transactional
    public SellerProduct upload(@PathVariable UUID id, @RequestParam String expectedVersion,
                                @RequestParam("files") List<MultipartFile> files) throws IOException {
        handler.findById(id); // ownership check first
        List<ProductExtras.Image> existing = images.findByProductIdOrderByDisplayOrder(id);
        List<ProductImageValidator.ImageInput> inputs = new ArrayList<>();
        for (MultipartFile f : files) inputs.add(new ProductImageValidator.ImageInput(f.getOriginalFilename(), f.getContentType(), f.getInputStream()));
        var validated = validator.validateBatch(inputs, existing.size());
        StoredProduct changed = application.changeImages(Actor.current().id(), id, version(expectedVersion));
        for (var image : validated) {
            int slot = firstFreeSlot(id);
            var stored = storage.store(image);
            removeFileOn(TransactionSynchronization.STATUS_ROLLED_BACK, stored.key());
            images.saveAndFlush(new ProductExtras.Image(id, stored.key(), image.format().contentType(),
                    stored.width(), stored.height(), stored.size(), slot));
        }
        return view(ProductManagementResponse.from(changed));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Transactional
    public SellerProduct deleteImage(@PathVariable UUID id, @PathVariable UUID imageId, @RequestParam String expectedVersion) {
        handler.findById(id); // ownership check first
        ProductExtras.Image image = images.findById(imageId).filter(i -> i.getProductId().equals(id))
                .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "Image not found"));
        StoredProduct changed = application.changeImages(Actor.current().id(), id, version(expectedVersion));
        images.delete(image);
        removeFileOn(TransactionSynchronization.STATUS_COMMITTED, image.getStorageKey());
        return view(ProductManagementResponse.from(changed));
    }

    /**
     * Files sit outside the transaction, so they follow its outcome. An unknown outcome keeps the file: a stray file beats a
     * broken image. Spring logs a failed delete. shortcut: a crash before this runs leaves an orphan, sweep the directory if that matters.
     */
    private void removeFileOn(int outcome, String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == outcome) storage.delete(key);
            }
        });
    }

    private int firstFreeSlot(UUID productId) {
        var used = images.findByProductIdOrderByDisplayOrder(productId).stream().map(ProductExtras.Image::getDisplayOrder).toList();
        for (int s = 1; s <= ProductImageValidator.MAX_IMAGES; s++) if (!used.contains(s)) return s;
        throw ApiException.conflict("IMAGE_COUNT_EXCEEDED", "A product can have at most five images");
    }

    private SellerProduct view(ProductManagementResponse product) {
        DependencyClients.Balance balance = dependencies.balance(product.sku());
        ProductExtras.Variant variant = groups.of(product.id()).orElse(null);
        List<VariantRow> rows = variant == null ? List.of() : groups.group(variant.getGroupId()).stream().map(this::row).toList();
        return new SellerProduct(product, details.findById(product.id()).map(ProductExtras.Details::getDescription).orElse(""),
                images.findByProductIdOrderByDisplayOrder(product.id()).stream().map(ImageView::of).toList(),
                balance == null ? null : balance.availableQuantity(), balance == null ? null : balance.reservedQuantity(),
                variant == null ? null : new VariantView(variant.getGroupId(), variant.getPosition(), variant.getSize(), variant.getColor()),
                rows);
    }

    private VariantRow row(ProductExtras.Variant variant) {
        StoredProduct stored = groups.current(variant.getProductId());
        var listing = stored.listing();
        DependencyClients.Balance balance = dependencies.balance(listing.sku());
        return new VariantRow(listing.id(), listing.sku(), listing.name(), variant.getSize(), variant.getColor(),
                listing.unitPrice().toBigIntegerExact().toString(), listing.status(), Long.toString(stored.version()),
                balance == null ? null : balance.availableQuantity());
    }

    static long version(String text) {
        if (text == null || !text.matches("0|[1-9][0-9]{0,17}")) throw ApiException.badRequest("VALIDATION_FAILED", "Invalid expectedVersion");
        return Long.parseLong(text);
    }
}
