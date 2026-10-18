package com.projecthk5.productservice.category;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository repository;
    private final CategoryHierarchyLock hierarchyLock;

    public CategoryService(CategoryRepository repository, CategoryHierarchyLock hierarchyLock) {
        this.repository = repository;
        this.hierarchyLock = hierarchyLock;
    }

    public List<CategoryResponse> list() {
        return repository.findAll(Sort.by("name", "id")).stream()
                .map(this::toResponse).toList();
    }

    public CategoryResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public CategoryResponse create(CategoryRequest request) {
        hierarchyLock.acquire();
        String name = validatedName(request);
        Category parent = validatedParent(null, request.parentId());
        return toResponse(repository.saveAndFlush(new Category(name, parent)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public CategoryResponse update(UUID id, CategoryRequest request) {
        hierarchyLock.acquire();
        Category category = find(id);
        String name = validatedName(request);
        Category parent = validatedParent(id, request.parentId());
        category.update(name, parent);
        return toResponse(repository.saveAndFlush(category));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void delete(UUID id) {
        hierarchyLock.acquire();
        Category category = find(id);
        if (repository.existsByParent_Id(id)) {
            throw new CategoryException(CategoryException.Code.CATEGORY_HAS_CHILDREN,
                    "Cannot delete a category that has children");
        }
        repository.delete(category);
        repository.flush();
    }

    private Category find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CategoryException(
                CategoryException.Code.CATEGORY_NOT_FOUND, "Category does not exist"));
    }

    private String validatedName(CategoryRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().length() > 255) {
            throw new CategoryException(CategoryException.Code.VALIDATION_ERROR,
                    "Category name must contain 1 to 255 characters and not be blank");
        }
        return request.name().strip();
    }

    private Category validatedParent(UUID categoryId, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(categoryId)) {
            throw cycle();
        }
        Category parent = repository.findById(parentId).orElseThrow(() -> new CategoryException(
                CategoryException.Code.CATEGORY_PARENT_NOT_FOUND, "Parent category does not exist"));
        Set<UUID> visited = new HashSet<>();
        for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(categoryId) || !visited.add(ancestor.getId())) {
                throw cycle();
            }
        }
        return parent;
    }

    private CategoryException cycle() {
        return new CategoryException(CategoryException.Code.CATEGORY_CYCLE,
                "Category parent relationship would contain a cycle");
    }

    private CategoryResponse toResponse(Category category) {
        Category parent = category.getParent();
        return new CategoryResponse(category.getId(), category.getName(),
                parent == null ? null : parent.getId());
    }
}
