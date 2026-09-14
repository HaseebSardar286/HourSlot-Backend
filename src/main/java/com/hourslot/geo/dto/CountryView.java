package com.hourslot.geo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryView {
    private String code;
    private String iso3;
    private String name;
    private String officialName;
    private String flag;
    private String region;
    private String subregion;
    private String capital;
    @Builder.Default
    private List<String> timezones = new ArrayList<>();
    @Builder.Default
    private List<CurrencyView> currencies = new ArrayList<>();
}
