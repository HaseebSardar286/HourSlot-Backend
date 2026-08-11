package com.hourslot.repository;

import com.hourslot.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrue();
    List<Category> findByParentIsNullAndActiveTrue();
    Optional<Category> findBySlug(String slug);
}
