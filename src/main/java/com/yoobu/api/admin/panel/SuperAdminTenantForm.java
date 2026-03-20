package com.yoobu.api.admin.panel;

import com.yoobu.api.tenant.TenantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SuperAdminTenantForm {

    @NotBlank
    private String slug;

    @NotBlank
    private String name;

    @NotNull
    private TenantType type = TenantType.FOOD_ORDER;

    private String botToken;

    private Long ownerTelegramId;

    private String timezone = "Asia/Ho_Chi_Minh";

    private String currency = "USD";

    private String primaryColor;

    private String logoUrl;

    private String welcomeMessage;

    private String checkoutNameHint;

    private String checkoutPhoneHint;

    private String checkoutNoteHint;

    private String paymentQrUrl;

    @NotBlank
    private String adminUsername;

    private String adminPassword;

    private boolean active = true;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TenantType getType() {
        return type;
    }

    public void setType(TenantType type) {
        this.type = type;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public Long getOwnerTelegramId() {
        return ownerTelegramId;
    }

    public void setOwnerTelegramId(Long ownerTelegramId) {
        this.ownerTelegramId = ownerTelegramId;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public String getCheckoutPhoneHint() {
        return checkoutPhoneHint;
    }

    public void setCheckoutPhoneHint(String checkoutPhoneHint) {
        this.checkoutPhoneHint = checkoutPhoneHint;
    }

    public String getCheckoutNameHint() {
        return checkoutNameHint;
    }

    public void setCheckoutNameHint(String checkoutNameHint) {
        this.checkoutNameHint = checkoutNameHint;
    }

    public String getCheckoutNoteHint() {
        return checkoutNoteHint;
    }

    public void setCheckoutNoteHint(String checkoutNoteHint) {
        this.checkoutNoteHint = checkoutNoteHint;
    }

    public String getPaymentQrUrl() {
        return paymentQrUrl;
    }

    public void setPaymentQrUrl(String paymentQrUrl) {
        this.paymentQrUrl = paymentQrUrl;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
