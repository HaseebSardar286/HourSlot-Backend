package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.Favorite;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FavoriteRepository {

    private static final String SELECT = """
            SELECT id, customer_user_id, business_id, created_at
            FROM favorites
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public FavoriteRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public List<Favorite> findByCustomerUser(User customerUser) {
        return jdbc.findList(SELECT + " WHERE customer_user_id = :customerUserId ORDER BY created_at DESC",
                jdbc.params().addValue("customerUserId", customerUser == null ? null : customerUser.getId()),
                rows.favorite);
    }

    public boolean existsByCustomerUserAndBusiness(User customerUser, Business business) {
        return jdbc.exists("""
                SELECT COUNT(*) FROM favorites
                WHERE customer_user_id = :customerUserId AND business_id = :businessId
                """, ids(customerUser, business));
    }

    public Optional<Favorite> findByCustomerUserAndBusiness(User customerUser, Business business) {
        return jdbc.findOne(SELECT + " WHERE customer_user_id = :customerUserId AND business_id = :businessId",
                ids(customerUser, business), rows.favorite);
    }

    public Favorite save(Favorite favorite) {
        if (favorite.getId() == null) {
            favorite.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO favorites (customer_user_id, business_id, created_at)
                    VALUES (:customerUserId, :businessId, :createdAt)
                    """, bind(favorite));
            favorite.setId(id);
            return favorite;
        }
        jdbc.update("""
                UPDATE favorites SET customer_user_id = :customerUserId, business_id = :businessId
                WHERE id = :id
                """, bind(favorite).addValue("id", favorite.getId()));
        return favorite;
    }

    public void delete(Favorite favorite) {
        jdbc.update("DELETE FROM favorites WHERE id = :id",
                jdbc.params().addValue("id", favorite.getId()));
    }

    private MapSqlParameterSource ids(User customerUser, Business business) {
        return jdbc.params()
                .addValue("customerUserId", customerUser == null ? null : customerUser.getId())
                .addValue("businessId", business == null ? null : business.getId());
    }

    private MapSqlParameterSource bind(Favorite favorite) {
        return jdbc.params()
                .addValue("customerUserId", favorite.getCustomerUser() == null ? null : favorite.getCustomerUser().getId())
                .addValue("businessId", favorite.getBusiness() == null ? null : favorite.getBusiness().getId())
                .addValue("createdAt", JdbcSupport.ts(favorite.getCreatedAt()));
    }
}
