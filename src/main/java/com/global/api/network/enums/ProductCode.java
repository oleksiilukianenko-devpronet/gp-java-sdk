package com.global.api.network.enums;

import com.global.api.entities.enums.IStringConstant;
import com.global.api.utils.StringUtils;

public enum ProductCode implements IStringConstant {
    Unleaded_Gas("01"),
    Unleaded_Premium_Gas("02"),
    Super_Premium_Gas("03"),
    Ethanol_Unleaded_Reg("04"),
    Ethanol_Unleaded_Mid_Grade("05"),
    Ethanol_Unleaded_Premium("06"),
    Ethanol_Unleaded_Super("07"),
    Ethanol_Regular_Leaded("08"),
    Methanol_Unleaded_Mid_Grade("09"),
    Methanol_Unleaded_Premium("10"),
    Methanol_Unleaded_Super("11"),
    Methanol_Regular_Leaded("12"),
    Methanol_Unleaded_Regular("13"),
    Regular_Leaded("14"),
    Mid_Grade_Gas("15"),
    No_2_Diesel("16"),
    Kerosene("17"),
    Propane("18"),
    CNG_Gas("19"),
    Jet_Fuel("20"),
    Unleaded_Reformulated("21"),
    Unleaded_Mid_Grade_Reformulated("22"),
    Unleaded_Premium_Gas_Reformulated("23"),
    Unleaded_Super_Reformulated("24"),
    Natural_Gas("25"),
    Gasohol_Gas_10_Percent("26"),
    Gasohol_Gas_7_Point_7_Percent("27"),
    Gasohol_Gas_5_Point_7_Percent("28"),
    White_Gas("29"),
    Dual_Propane_Unleaded("30"),
    Wide_nozzle_unleaded("31"),
    Marine_Fuel("32"),
    Motor_Fuel("33"),
    Methanol_85("34"),
    Ethanol_85("35"),
    No_1_Diesel("36"),
    Aviation_Gas("37"),
    Military_Fuel("38"),
    Other_Fuel("39"),
    Motor_Oils("50"),
    Oil_Change("51"),
    Automotive_Products("60"),
    Automotive_Glass("61"),
    Car_Wash("62"),
    Lamps("63"),
    Wipers("64"),
    Fluids_and_Coolant("65"),
    Hoses_and_Belts("66"),
    Tires("67"),
    Filters("68"),
    Batteries("69"),
    Repairs_Services("70"),
    Engine_Service("71"),
    Transmission_Service("72"),
    Brake_Service("73"),
    Towing("74"),
    Tuneup("75"),
    Inspection("76"),
    Storage("77"),
    Labor("78"),
    Reserved("79"),
    Groceries("80"),
    Cigarettes_Tobacco("81"),
    Soda("82"),
    Health_Beauty_Aid("83"),
    Milk_Juice("84"),
    Misc_Beverage("85"),
    Restaurant("86"),
    Beer_and_Wine("87"),
    Miscellaneous("90"),
    Federal_excise_tax_on_tire_lube("97"),
    Sales_tax("98"),
    Discount("99");

    private final String value;
    ProductCode(String value) {
        this.value = value;
    }

    public String getValue() { return this.value; }
    public String getLongValue() { return StringUtils.padLeft(this.value,3,'0'); }

    public byte[] getBytes() { return this.value.getBytes(); }
}
