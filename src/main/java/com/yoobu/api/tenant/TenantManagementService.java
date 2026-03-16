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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private static final String ENTITY_NAME = "tenant";

    private final AuditLogService auditLogService;
    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> getAllTenants() {
        return tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenantMapper::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantDetailResponse getTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        Map<String, String> config = new LinkedHashMap<>();
        tenantConfigRepository.findByTenantId(tenantId)
                .forEach(entry -> config.put(entry.getKey(), entry.getValue()));

        return tenantMapper.toDetailResponse(tenant, config);
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
        tenant.setName(request.name());
        tenant.setType(request.type());
        tenant.setBotToken(normalizeOptional(request.botToken()));
        tenant.setOwnerTelegramId(request.ownerTelegramId());
        tenant.setTimezone(normalizeTimezone(request.timezone()));
        tenant.setActive(true);
        tenant.setCreatedAt(now);

        Tenant savedTenant = tenantRepository.save(tenant);

        List<TenantConfig> configs = new ArrayList<>();
        addConfig(configs, savedTenant, "admin_username", request.adminUsername());
        addConfig(configs, savedTenant, "admin_password", passwordEncoder.encode(request.adminPassword()));
        addConfig(configs, savedTenant, "primary_color", request.primaryColor());
        addConfig(configs, savedTenant, "logo_url", request.logoUrl());
        addConfig(configs, savedTenant, "welcome_message", request.welcomeMessage());
        tenantConfigRepository.saveAll(configs);
        auditLogService.logCreate(
                savedTenant.getId(),
                ENTITY_NAME,
                savedTenant.getId(),
                auditLogService.currentActorId(),
                toAuditSnapshotFromValues(savedTenant, configMap(configs))
        );

        return tenantMapper.toSummaryResponse(savedTenant);
    }

    @Transactional
    public TenantSummaryResponse updateTenant(Long tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        Map<String, TenantConfig> existingConfigs = new HashMap<>();
        tenantConfigRepository.findByTenantId(tenantId)
                .forEach(entry -> existingConfigs.put(entry.getKey(), entry));
        Map<String, Object> oldSnapshot = toAuditSnapshot(tenant, existingConfigs);

        tenant.setName(request.name());
        tenant.setType(request.type());
        tenant.setBotToken(normalizeOptional(request.botToken()));
        tenant.setOwnerTelegramId(request.ownerTelegramId());
        tenant.setTimezone(normalizeTimezone(request.timezone()));
        tenant.setActive(request.active());

        Tenant savedTenant = tenantRepository.save(tenant);

        upsertConfig(existingConfigs, savedTenant, "admin_username", request.adminUsername(), false);
        if (StringUtils.hasText(request.adminPassword())) {
            upsertConfig(existingConfigs, savedTenant, "admin_password", passwordEncoder.encode(request.adminPassword()), false);
        }
        upsertConfig(existingConfigs, savedTenant, "primary_color", request.primaryColor(), true);
        upsertConfig(existingConfigs, savedTenant, "logo_url", request.logoUrl(), true);
        upsertConfig(existingConfigs, savedTenant, "welcome_message", request.welcomeMessage(), true);
        auditLogService.logUpdate(
                savedTenant.getId(),
                ENTITY_NAME,
                savedTenant.getId(),
                auditLogService.currentActorId(),
                oldSnapshot,
                toAuditSnapshot(savedTenant, existingConfigs)
        );

        return tenantMapper.toSummaryResponse(savedTenant);
    }

    private void addConfig(List<TenantConfig> configs, Tenant tenant, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(value);
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

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeTimezone(String timezone) {
        return StringUtils.hasText(timezone) ? timezone.trim() : "Asia/Ho_Chi_Minh";
    }

    private Map<String, String> configMap(List<TenantConfig> configs) {
        Map<String, String> values = new HashMap<>();
        configs.forEach(config -> values.put(config.getKey(), config.getValue()));
        return values;
    }

    private Map<String, Object> toAuditSnapshot(Tenant tenant, Map<String, TenantConfig> configs) {
        Map<String, String> values = new HashMap<>();
        configs.forEach((key, config) -> values.put(key, config.getValue()));
        return toAuditSnapshotFromValues(tenant, values);
    }

    private Map<String, Object> toAuditSnapshotFromValues(Tenant tenant, Map<String, String> configValues) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", tenant.getId());
        snapshot.put("slug", tenant.getSlug());
        snapshot.put("name", tenant.getName());
        snapshot.put("type", tenant.getType());
        snapshot.put("active", tenant.isActive());
        snapshot.put("timezone", tenant.getTimezone());
        snapshot.put("ownerTelegramId", tenant.getOwnerTelegramId());
        snapshot.put("botTokenConfigured", StringUtils.hasText(tenant.getBotToken()));
        snapshot.put("adminUsername", configValues.get("admin_username"));
        snapshot.put("adminPasswordConfigured", configValues.containsKey("admin_password"));
        snapshot.put("primaryColor", configValues.get("primary_color"));
        snapshot.put("logoUrl", configValues.get("logo_url"));
        snapshot.put("welcomeMessage", configValues.get("welcome_message"));
        return snapshot;
    }
}
