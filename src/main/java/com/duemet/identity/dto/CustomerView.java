package com.duemet.identity.dto;

import com.duemet.identity.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerView {
    private Long id;
    private User user;
}
