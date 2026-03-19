package com.yoobu.api.tenant;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private static final String ENTITY_NAME = "tenant";
    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    private final AuditLogService auditLogService;
    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final TenantSettingsService tenantSettingsService;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> getAllTenants() {
        return tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenantMapper::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TenantSummaryResponse> getAllTenantsPage(String query, int page, int size) {
        String normalizedQuery = normalizeOptional(query);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Tenant> tenantPage = StringUtils.hasText(normalizedQuery)
                ? tenantRepository.findByNameContainingIgnoreCaseOrSlugContainingIgnoreCase(
                        normalizedQuery,
                        normalizedQuery,
                        pageable
                )
                : tenantRepository.findAll(pageable);

        return tenantPage.map(tenantMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public TenantDetailResponse getTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        TenantSettings settings = tenantSettingsService.getSettings(tenantId);

        return tenantMapper.toDetailResponse(tenant, settings.asMap());
    }

    @Transactional(readOnly = true)
    public boolean isSlugAvailable(String slug) {
        String normalizedSlug = normalizeOptional(slug);
        return StringUtils.hasText(normalizedSlug) && !tenantRepository.existsBySlug(normalizedSlug);
    }

    @Transactional
    public TenantSummaryResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant slug already exists");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Tenant tenant = new Tenant();
        tenant.setSlug(request.slug());
        applyTenantDetails(
                tenant,
                request.name(),
                request.type(),
                request.botToken(),
                request.ownerTelegramId(),
                request.timezone(),
                true
        );
        tenant.setCreatedAt(now);

        Tenant savedTenant = tenantRepository.save(tenant);
        List<TenantConfig> configs = buildInitialConfigs(savedTenant, request);
        tenantConfigRepository.saveAll(configs);
        auditLogService.logCreate(
                savedTenant.getId(),
                ENTITY_NAME,
                savedTenant.getId(),
                auditLogService.currentActorId(),
                toAuditSnapshot(savedTenant, TenantSettings.fromEntries(configs))
        );

        return tenantMapper.toSummaryResponse(savedTenant);
    }

    @Transactional
    public TenantSummaryResponse updateTenant(Long tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        Map<String, TenantConfig> existingConfigs = loadConfigsByKey(tenantId);
        Map<String, Object> oldSnapshot = toAuditSnapshot(tenant, existingConfigs);

        applyTenantDetails(
                tenant,
                request.name(),
                request.type(),
                request.botToken(),
                request.ownerTelegramId(),
                request.timezone(),
                request.active()
        );

        Tenant savedTenant = tenantRepository.save(tenant);
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.ADMIN_USERNAME, request.adminUsername(), false);
        upsertCurrency(existingConfigs, savedTenant, request.currency());
        if (StringUtils.hasText(request.adminPassword())) {
            upsertConfig(
                    existingConfigs,
                    savedTenant,
                    TenantConfigKeys.ADMIN_PASSWORD,
                    passwordEncoder.encode(request.adminPassword()),
                    false
            );
        }
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.PRIMARY_COLOR, request.primaryColor(), true);
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.LOGO_URL, request.logoUrl(), true);
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.WELCOME_MESSAGE, request.welcomeMessage(), true);
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.CHECKOUT_NAME_HINT, request.checkoutNameHint(), true);
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.CHECKOUT_PHONE_HINT, request.checkoutPhoneHint(), true);
        upsertConfig(existingConfigs, savedTenant, TenantConfigKeys.CHECKOUT_NOTE_HINT, request.checkoutNoteHint(), true);
        auditLogService.logUpdate(
                savedTenant.getId(),
                ENTITY_NAME,
                savedTenant.getId(),
                auditLogService.currentActorId(),
                oldSnapshot,
                toAuditSnapshot(savedTenant, TenantSettings.fromEntries(new ArrayList<>(existingConfigs.values())))
        );

        return tenantMapper.toSummaryResponse(savedTenant);
    }

    private void applyTenantDetails(
            Tenant tenant,
            String name,
            TenantType type,
            String botToken,
            Long ownerTelegramId,
            String timezone,
            boolean active
    ) {
        tenant.setName(name);
        tenant.setType(type);
        tenant.setBotToken(normalizeOptional(botToken));
        tenant.setOwnerTelegramId(ownerTelegramId);
        tenant.setTimezone(normalizeTimezone(timezone));
        tenant.setActive(active);
    }

    private List<TenantConfig> buildInitialConfigs(Tenant tenant, CreateTenantRequest request) {
        List<TenantConfig> configs = new ArrayList<>();
        addConfig(configs, tenant, TenantConfigKeys.ADMIN_USERNAME, request.adminUsername());
        addConfig(configs, tenant, TenantConfigKeys.ADMIN_PASSWORD, passwordEncoder.encode(request.adminPassword()));
        addConfig(configs, tenant, TenantConfigKeys.CURRENCY, resolveCurrency(request.currency()));
        addConfig(configs, tenant, TenantConfigKeys.PRIMARY_COLOR, request.primaryColor());
        addConfig(configs, tenant, TenantConfigKeys.LOGO_URL, request.logoUrl());
        addConfig(configs, tenant, TenantConfigKeys.WELCOME_MESSAGE, request.welcomeMessage());
        addConfig(configs, tenant, TenantConfigKeys.CHECKOUT_NAME_HINT, request.checkoutNameHint());
        addConfig(configs, tenant, TenantConfigKeys.CHECKOUT_PHONE_HINT, request.checkoutPhoneHint());
        addConfig(configs, tenant, TenantConfigKeys.CHECKOUT_NOTE_HINT, request.checkoutNoteHint());
        return configs;
    }

    private Map<String, TenantConfig> loadConfigsByKey(Long tenantId) {
        Map<String, TenantConfig> configsByKey = new HashMap<>();
        tenantConfigRepository.findByTenantId(tenantId)
                .forEach(entry -> configsByKey.put(entry.getKey(), entry));
        return configsByKey;
    }

    private void addConfig(List<TenantConfig> configs, Tenant tenant, String key, String value) {
        String normalizedValue = normalizeOptional(value);
        if (normalizedValue == null) {
            return;
        }
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(normalizedValue);
        configs.add(config);
    }

    private void upsertConfig(
            Map<String, TenantConfig> existingConfigs,
            Tenant tenant,
            String key,
            String value,
            boolean removeWhenBlank
    ) {
        TenantConfig existing = existingConfigs.get(key);
        String normalizedValue = normalizeOptional(value);

        if (!StringUtils.hasText(normalizedValue)) {
            if (removeWhenBlank && existing != null) {
                tenantConfigRepository.delete(existing);
                existingConfigs.remove(key);
            }
            return;
        }

        TenantConfig config = existing != null ? existing : new TenantConfig();
        if (existing == null) {
            config.setTenant(tenant);
            config.setKey(key);
        }
        config.setValue(normalizedValue);
        TenantConfig savedConfig = tenantConfigRepository.save(config);
        existingConfigs.put(key, savedConfig);
    }

    private void upsertCurrency(
            Map<String, TenantConfig> existingConfigs,
            Tenant tenant,
            String requestedCurrency
    ) {
        String normalizedCurrency = normalizeOptional(requestedCurrency);
        if (StringUtils.hasText(normalizedCurrency)) {
            upsertConfig(existingConfigs, tenant, TenantConfigKeys.CURRENCY, normalizedCurrency, false);
            return;
        }

        if (!existingConfigs.containsKey(TenantConfigKeys.CURRENCY)) {
            upsertConfig(existingConfigs, tenant, TenantConfigKeys.CURRENCY, TenantSettings.DEFAULT_CURRENCY, false);
        }
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeTimezone(String timezone) {
        return StringUtils.hasText(timezone) ? timezone.trim() : DEFAULT_TIMEZONE;
    }

    private String resolveCurrency(String currency) {
        String normalizedCurrency = normalizeOptional(currency);
        if (StringUtils.hasText(normalizedCurrency)) {
            return normalizedCurrency;
        }
        return TenantSettings.DEFAULT_CURRENCY;
    }

    private Map<String, Object> toAuditSnapshot(Tenant tenant, Map<String, TenantConfig> configs) {
        return toAuditSnapshot(tenant, TenantSettings.fromEntries(new ArrayList<>(configs.values())));
    }

    private Map<String, Object> toAuditSnapshot(Tenant tenant, TenantSettings settings) {
        TenantSettings.AdminSettings admin = settings.admin();
        TenantSettings.BrandingSettings branding = settings.branding();
        TenantSettings.CheckoutSettings checkout = settings.checkout();
        TenantSettings.PricingSettings pricing = settings.pricing();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", tenant.getId());
        snapshot.put("slug", tenant.getSlug());
        snapshot.put("name", tenant.getName());
        snapshot.put("type", tenant.getType());
        snapshot.put("active", tenant.isActive());
        snapshot.put("timezone", tenant.getTimezone());
        snapshot.put("ownerTelegramId", tenant.getOwnerTelegramId());
        snapshot.put("botTokenConfigured", StringUtils.hasText(tenant.getBotToken()));
        snapshot.put("adminUsername", admin.username());
        snapshot.put("adminPasswordConfigured", admin.passwordConfigured());
        snapshot.put("primaryColor", branding.primaryColor());
        snapshot.put("logoUrl", branding.logoUrl());
        snapshot.put("welcomeMessage", branding.welcomeMessage());
        snapshot.put("checkoutNameHint", checkout.nameHint());
        snapshot.put("checkoutPhoneHint", checkout.phoneHint());
        snapshot.put("checkoutNoteHint", checkout.noteHint());
        snapshot.put("currency", pricing.currency());
        return snapshot;
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize < 1) {
            return 20;
        }
        return Math.min(requestedSize, 100);
    }
}
