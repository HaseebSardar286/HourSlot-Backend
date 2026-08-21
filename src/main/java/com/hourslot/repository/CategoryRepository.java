package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Category;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CategoryRepository {

    private static final String SELECT = """
            SELECT id, parent_id, name, slug, icon, image_url, search_tags, is_active, sort_order, created_at
            FROM categories
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public CategoryRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<Category> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), rows.category);
    }

    public List<Category> findAllById(Iterable<Long> ids) {
        List<Long> idList = toIdList(ids);
        if (idList.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT + " WHERE id IN (:ids)", jdbc.params().addValue("ids", idList), rows.category);
    }

    public Category save(Category category) {
        if (category.getId() == null) {
            category.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO categories (parent_id, name, slug, icon, image_url, search_tags, is_active, sort_order, created_at)
                    VALUES (:parentId, :name, :slug, :icon, :imageUrl, :searchTags, :active, :sortOrder, :createdAt)
                    """, bind(category));
            category.setId(id);
            return category;
        }
        jdbc.update("""
                UPDATE categories SET parent_id = :parentId, name = :name, slug = :slug, icon = :icon,
                    image_url = :imageUrl, search_tags = :searchTags, is_active = :active, sort_order = :sortOrder,
                    updated_at = NOW()
                WHERE id = :id
                """, bind(category).addValue("id", category.getId()));
        return category;
    }

    public List<Category> findByActiveTrue() {
        return jdbc.findList(SELECT + " WHERE is_active = TRUE ORDER BY sort_order, id", jdbc.params(), rows.category);
    }

    public List<Category> findByParentIsNullAndActiveTrue() {
        return jdbc.findList(SELECT + " WHERE parent_id IS NULL AND is_active = TRUE ORDER BY sort_order, id",
                jdbc.params(), rows.category);
    }

    public Optional<Category> findBySlug(String slug) {
        return jdbc.findOne(SELECT + " WHERE slug = :slug", jdbc.params().addValue("slug", slug), rows.category);
    }

    private MapSqlParameterSource bind(Category category) {
        return jdbc.params()
                .addValue("parentId", category.getParent() == null ? null : category.getParent().getId())
                .addValue("name", category.getName())
                .addValue("slug", category.getSlug())
                .addValue("icon", category.getIcon())
                .addValue("imageUrl", category.getImageUrl())
                .addValue("searchTags", category.getSearchTags())
                .addValue("active", category.isActive())
                .addValue("sortOrder", category.getSortOrder())
                .addValue("createdAt", JdbcSupport.ts(category.getCreatedAt()));
    }

    private static List<Long> toIdList(Iterable<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        List<Long> list = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                list.add(id);
            }
        }
        return list;
    }
}
