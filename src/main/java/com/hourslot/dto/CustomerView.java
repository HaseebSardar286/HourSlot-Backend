package com.hourslot.dto;

import com.hourslot.model.User;
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
