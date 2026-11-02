package com.projecthk5.productservice.category;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByParent_Id(UUID parentId);

    boolean existsBySlug(String slug);

    @Query(nativeQuery = true, value = """
            WITH RECURSIVE chain AS (
                SELECT id, parent_id, status FROM categories WHERE id = :id
                UNION
                SELECT c.id, c.parent_id, c.status FROM categories c JOIN chain ON c.id = chain.parent_id
            )
            SELECT EXISTS (SELECT 1 FROM chain WHERE status <> 'ACTIVE')""")
    boolean isHiddenInChain(@Param("id") UUID id);
}
