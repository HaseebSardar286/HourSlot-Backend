package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Branch;
import com.hourslot.model.BranchHoliday;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class BranchHolidayRepository {

    private static final String SELECT = """
            SELECT id, branch_id, holiday_date, description
            FROM branch_holidays
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BranchHolidayRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<BranchHoliday> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), rows.branchHoliday);
    }

    public BranchHoliday save(BranchHoliday holiday) {
        if (holiday.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO branch_holidays (branch_id, holiday_date, description)
                    VALUES (:branchId, :holidayDate, :description)
                    """, bind(holiday));
            holiday.setId(id);
            return holiday;
        }
        jdbc.update("""
                UPDATE branch_holidays SET branch_id = :branchId, holiday_date = :holidayDate, description = :description
                WHERE id = :id
                """, bind(holiday).addValue("id", holiday.getId()));
        return holiday;
    }

    public void delete(BranchHoliday holiday) {
        jdbc.update("DELETE FROM branch_holidays WHERE id = :id", jdbc.params().addValue("id", holiday.getId()));
    }

    public List<BranchHoliday> findByBranchOrderByHolidayDateAsc(Branch branch) {
        return jdbc.findList(SELECT + " WHERE branch_id = :branchId ORDER BY holiday_date ASC",
                jdbc.params().addValue("branchId", branch.getId()), rows.branchHoliday);
    }

    public List<BranchHoliday> findByBranchAndHolidayDate(Branch branch, LocalDate holidayDate) {
        return jdbc.findList(SELECT + " WHERE branch_id = :branchId AND holiday_date = :holidayDate",
                jdbc.params().addValue("branchId", branch.getId()).addValue("holidayDate", date(holidayDate)),
                rows.branchHoliday);
    }

    private MapSqlParameterSource bind(BranchHoliday holiday) {
        return jdbc.params()
                .addValue("branchId", holiday.getBranch() == null ? null : holiday.getBranch().getId())
                .addValue("holidayDate", date(holiday.getHolidayDate()))
                .addValue("description", holiday.getDescription());
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
