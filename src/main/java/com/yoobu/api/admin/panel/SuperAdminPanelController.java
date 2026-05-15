package com.yoobu.api.admin.panel;

import com.yoobu.api.audit.AuditLogChangeFormatter;
import com.yoobu.api.audit.AuditLogCsvExporter;
import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.audit.dto.AuditLogItemResponse;
import com.yoobu.api.media.ImageServiceClient;
import com.yoobu.api.tenant.TenantManagementService;
import com.yoobu.api.tenant.TenantConfigKeys;
import com.yoobu.api.tenant.TenantSettings;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/superadmin/panel")
public class SuperAdminPanelController {

    private static final String TENANTS_REDIRECT = "redirect:/superadmin/panel/tenants";
    private static final String TENANTS_VIEW = "superadmin/panel/tenants";
    private static final String TENANT_DETAIL_VIEW = "superadmin/panel/tenant-detail";
    private static final String TENANT_FORM_VIEW = "superadmin/panel/tenant-form";
    private static final String AUDIT_VIEW = "superadmin/panel/audit";
    private static final String CREATE_MODE = "create";
    private static final String EDIT_MODE = "edit";
    private static final String FLASH_TYPE_SUCCESS = "success";
    private static final DateTimeFormatter DATETIME_LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Map<String, String> ENTITY_LABELS = buildEntityLabels();
    private static final Map<String, String> ACTION_LABELS = buildActionLabels();
    private final TenantManagementService tenantManagementService;
    private final AuditLogService auditLogService;
    private final AuditLogChangeFormatter auditLogChangeFormatter;
    private final AuditLogCsvExporter auditLogCsvExporter;
    private final ImageServiceClient imageServiceClient;

    @GetMapping({"", "/"})
    public String panelHome() {
        return TENANTS_REDIRECT;
    }

    @GetMapping("/tenants")
    public String tenants(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        populateTenantsModel(query, page, size, model);
        return TENANTS_VIEW;
    }

    @GetMapping("/audit")
    public String audit(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        OffsetDateTime createdFromValue = parseDateTime(createdFrom);
        OffsetDateTime createdToValue = parseDateTime(createdTo);

        Page<AuditLogItemResponse> auditPage =
                auditLogService.search(tenantId, entity, action, actorId, createdFromValue, createdToValue, page, size);
        model.addAttribute("auditEntries", auditPage.getContent());
        model.addAttribute("auditDiffById", buildAuditDiffById(auditPage.getContent()));
        model.addAttribute("auditPage", auditPage);
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("entity", entity);
        model.addAttribute("action", action);
        model.addAttribute("actorId", actorId);
        model.addAttribute("createdFrom", toDateTimeLocalValue(createdFromValue));
        model.addAttribute("createdTo", toDateTimeLocalValue(createdToValue));
        model.addAttribute("size", auditPage.getSize());
        model.addAttribute("exportLimit", auditLogService.exportLimit());
        model.addAttribute("exportTruncated", auditPage.getTotalElements() > auditLogService.exportLimit());
        model.addAttribute("entityLabels", ENTITY_LABELS);
        model.addAttribute("actionLabels", ACTION_LABELS);
        return AUDIT_VIEW;
    }

    @GetMapping(value = "/audit/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAudit(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "5000") int size
    ) {
        OffsetDateTime createdFromValue = parseDateTime(createdFrom);
        OffsetDateTime createdToValue = parseDateTime(createdTo);
        var items = auditLogService.searchForExport(tenantId, entity, action, actorId, createdFromValue, createdToValue, size);
        String csv = auditLogCsvExporter.toCsv(items);
        String filename = "audit-log-" + OffsetDateTime.now(ZoneOffset.UTC).format(EXPORT_TIMESTAMP_FORMATTER) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/tenants/{tenantId}")
    public String tenantDetail(@PathVariable Long tenantId, Model model) {
        model.addAttribute("tenant", tenantManagementService.getTenant(tenantId));
        return TENANT_DETAIL_VIEW;
    }

    @GetMapping("/tenants/new")
    public String newTenant(Model model) {
        model.addAttribute("tenantForm", new SuperAdminTenantForm());
        model.addAttribute("tenantTypes", TenantType.values());
        model.addAttribute("formMode", CREATE_MODE);
        return TENANT_FORM_VIEW;
    }

    @PostMapping("/tenants")
    public String createTenant(
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        validateCreateForm(form, bindingResult);

        if (bindingResult.hasErrors()) {
            return populateFormModel(model, CREATE_MODE);
        }

        try {
            tenantManagementService.createTenant(toRequest(form));
        } catch (ResponseStatusException ex) {
            return formError(model, CREATE_MODE, ex);
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Tenant created.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return TENANTS_REDIRECT;
    }

    @GetMapping("/tenants/{tenantId}/edit")
    public String editTenant(@PathVariable Long tenantId, Model model) {
        TenantDetailResponse tenant = tenantManagementService.getTenant(tenantId);
        model.addAttribute("tenantForm", toForm(tenant));
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("currentQrUrl", tenant.config().get(TenantConfigKeys.PAYMENT_QR_URL));
        model.addAttribute("currentLogoUrl", tenant.config().get(TenantConfigKeys.LOGO_URL));
        model.addAttribute("currentBannerUrl", tenant.config().get(TenantConfigKeys.BANNER_URL));
        return populateFormModel(model, EDIT_MODE);
    }

    @PostMapping("/tenants/{tenantId}/logo")
    public String uploadLogo(
            @PathVariable Long tenantId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes
    ) {
        try {
            TenantDetailResponse tenant = tenantManagementService.getTenant(tenantId);
            String oldUrl = tenant.config().get(TenantConfigKeys.LOGO_URL);
            String cdnUrl = imageServiceClient.upload(tenantId, "config/logo", file);
            tenantManagementService.updateLogoUrl(tenantId, cdnUrl);
            if (oldUrl != null) {
                imageServiceClient.deleteByUrl(oldUrl);
            }
            redirectAttributes.addFlashAttribute("flashMessage", "Logo updated.");
            redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", ex.getReason());
            redirectAttributes.addFlashAttribute("flashType", "error");
        }
        return "redirect:/superadmin/panel/tenants/" + tenantId + "/edit";
    }

    @PostMapping("/tenants/{tenantId}/banner")
    public String uploadBanner(
            @PathVariable Long tenantId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes
    ) {
        try {
            TenantDetailResponse tenant = tenantManagementService.getTenant(tenantId);
            String oldUrl = tenant.config().get(TenantConfigKeys.BANNER_URL);
            String cdnUrl = imageServiceClient.upload(tenantId, "config/banner", file);
            tenantManagementService.updateBannerUrl(tenantId, cdnUrl);
            if (oldUrl != null) {
                imageServiceClient.deleteByUrl(oldUrl);
            }
            redirectAttributes.addFlashAttribute("flashMessage", "Banner updated.");
            redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", ex.getReason());
            redirectAttributes.addFlashAttribute("flashType", "error");
        }
        return "redirect:/superadmin/panel/tenants/" + tenantId + "/edit";
    }

    @PostMapping("/tenants/{tenantId}/payment-qr")
    public String uploadPaymentQr(
            @PathVariable Long tenantId,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes
    ) {
        try {
            TenantDetailResponse tenant = tenantManagementService.getTenant(tenantId);
            String oldUrl = tenant.config().get(TenantConfigKeys.PAYMENT_QR_URL);
            String cdnUrl = imageServiceClient.upload(tenantId, "payment/qr", file);
            tenantManagementService.updatePaymentQrUrl(tenantId, cdnUrl);
            if (oldUrl != null) {
                imageServiceClient.deleteByUrl(oldUrl);
            }
            redirectAttributes.addFlashAttribute("flashMessage", "Payment QR updated.");
            redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("flashMessage", ex.getReason());
            redirectAttributes.addFlashAttribute("flashType", "error");
        }
        return "redirect:/superadmin/panel/tenants/" + tenantId + "/edit";
    }

    @PostMapping("/tenants/{tenantId}")
    public String updateTenant(
            @PathVariable Long tenantId,
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return populateEditFormModel(model, tenantId);
        }

        try {
            tenantManagementService.updateTenant(tenantId, toUpdateRequest(form));
        } catch (ResponseStatusException ex) {
            return formError(populateTenantId(model, tenantId), EDIT_MODE, ex);
        }

        redirectAttributes.addFlashAttribute("flashMessage", "Tenant updated.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return tenantDetailRedirect(tenantId);
    }

    private CreateTenantRequest toRequest(SuperAdminTenantForm form) {
        return new CreateTenantRequest(
                form.getSlug(),
                form.getName(),
                form.getType(),
                form.getBotToken(),
                form.getOwnerTelegramId(),
                form.getTimezone(),
                form.getCurrency(),
                form.getPrimaryColor(),
                form.getLogoUrl(),
                form.getBannerUrl(),
                form.getWelcomeMessage(),
                form.getCheckoutNameHint(),
                form.getCheckoutPhoneHint(),
                form.getCheckoutNoteHint(),
                form.getCheckoutDeliveryHint(),
                form.getPaymentQrUrl(),
                form.getPaymentBankBin(),
                form.getPaymentAccountNumber(),
                form.getAdminUsername(),
                form.getAdminPassword(),
                form.getCutoffHour(),
                form.getCutoffMinute()
        );
    }

    private UpdateTenantRequest toUpdateRequest(SuperAdminTenantForm form) {
        return new UpdateTenantRequest(
                form.getName(),
                form.getType(),
                form.getBotToken(),
                form.getOwnerTelegramId(),
                form.getTimezone(),
                form.getCurrency(),
                form.getPrimaryColor(),
                form.getLogoUrl(),
                form.getBannerUrl(),
                form.getWelcomeMessage(),
                form.getCheckoutNameHint(),
                form.getCheckoutPhoneHint(),
                form.getCheckoutNoteHint(),
                form.getCheckoutDeliveryHint(),
                form.getPaymentQrUrl(),
                form.getPaymentBankBin(),
                form.getPaymentAccountNumber(),
                form.getAdminUsername(),
                form.getAdminPassword(),
                form.isActive(),
                form.getCutoffHour(),
                form.getCutoffMinute()
        );
    }

    private SuperAdminTenantForm toForm(TenantDetailResponse tenant) {
        Map<String, String> config = tenant.config();
        SuperAdminTenantForm form = new SuperAdminTenantForm();
        form.setSlug(tenant.slug());
        form.setName(tenant.name());
        form.setType(tenant.type());
        form.setBotToken(tenant.botToken());
        form.setOwnerTelegramId(tenant.ownerTelegramId());
        form.setTimezone(tenant.timezone());
        form.setCurrency(config.getOrDefault(TenantConfigKeys.CURRENCY, TenantSettings.DEFAULT_CURRENCY));
        form.setPrimaryColor(config.get(TenantConfigKeys.PRIMARY_COLOR));
        form.setLogoUrl(config.get(TenantConfigKeys.LOGO_URL));
        form.setBannerUrl(config.get(TenantConfigKeys.BANNER_URL));
        form.setWelcomeMessage(config.get(TenantConfigKeys.WELCOME_MESSAGE));
        form.setCheckoutNameHint(config.get(TenantConfigKeys.CHECKOUT_NAME_HINT));
        form.setCheckoutPhoneHint(config.get(TenantConfigKeys.CHECKOUT_PHONE_HINT));
        form.setCheckoutNoteHint(config.get(TenantConfigKeys.CHECKOUT_NOTE_HINT));
        form.setCheckoutDeliveryHint(config.get(TenantConfigKeys.CHECKOUT_DELIVERY_HINT));
        form.setPaymentQrUrl(config.get(TenantConfigKeys.PAYMENT_QR_URL));
        form.setPaymentBankBin(config.get(TenantConfigKeys.PAYMENT_BANK_BIN));
        form.setPaymentAccountNumber(config.get(TenantConfigKeys.PAYMENT_ACCOUNT_NUMBER));
        form.setAdminUsername(config.get(TenantConfigKeys.ADMIN_USERNAME));
        form.setActive(tenant.active());
        form.setCutoffHour(parseIntOrNull(config.get(TenantConfigKeys.CUTOFF_HOUR)));
        form.setCutoffMinute(parseIntOrNull(config.get(TenantConfigKeys.CUTOFF_MINUTE)));
        return form;
    }

    private String populateFormModel(Model model, String formMode) {
        model.addAttribute("tenantTypes", TenantType.values());
        model.addAttribute("formMode", formMode);
        return TENANT_FORM_VIEW;
    }

    private void populateTenantsModel(String query, int page, int size, Model model) {
        Page<TenantSummaryResponse> tenantPage = tenantManagementService.getAllTenantsPage(query, page, size);
        model.addAttribute("tenants", tenantPage.getContent());
        model.addAttribute("tenantPage", tenantPage);
        model.addAttribute("query", query);
        model.addAttribute("size", tenantPage.getSize());
    }

    private void validateCreateForm(SuperAdminTenantForm form, BindingResult bindingResult) {
        if (!StringUtils.hasText(form.getAdminPassword())) {
            bindingResult.rejectValue("adminPassword", "tenantForm.adminPassword", "must not be blank");
        }
        if (StringUtils.hasText(form.getSlug()) && !tenantManagementService.isSlugAvailable(form.getSlug())) {
            bindingResult.rejectValue("slug", "tenantForm.slug", "Tenant slug already exists");
        }
    }

    private String formError(Model model, String formMode, ResponseStatusException ex) {
        model.addAttribute("formError", ex.getReason());
        return populateFormModel(model, formMode);
    }

    private String populateEditFormModel(Model model, Long tenantId) {
        return populateFormModel(populateTenantId(model, tenantId), EDIT_MODE);
    }

    private Model populateTenantId(Model model, Long tenantId) {
        model.addAttribute("tenantId", tenantId);
        return model;
    }

    private String tenantDetailRedirect(Long tenantId) {
        return TENANTS_REDIRECT + "/" + tenantId;
    }

    private OffsetDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException fallbackError) {
                return null;
            }
        }
    }

    private String toDateTimeLocalValue(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).format(DATETIME_LOCAL_FORMATTER);
    }

    private static Map<String, String> buildEntityLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("tenant", "Tenant");
        labels.put("service", "Service");
        labels.put("booking", "Booking");
        return labels;
    }

    private static Map<String, String> buildActionLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("CREATE", "Created");
        labels.put("UPDATE", "Updated");
        labels.put("DELETE", "Deleted");
        labels.put("UPDATE_STATUS", "Status updated");
        labels.put("CONFIRM_PAYMENT", "Payment confirmed by client");
        labels.put("CANCEL", "Cancelled");
        return labels;
    }

    private Integer parseIntOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Long, List<String>> buildAuditDiffById(List<AuditLogItemResponse> entries) {
        Map<Long, List<String>> diffById = new LinkedHashMap<>();
        for (AuditLogItemResponse entry : entries) {
            diffById.put(entry.id(), auditLogChangeFormatter.buildDiffLines(entry));
        }
        return diffById;
    }
}
