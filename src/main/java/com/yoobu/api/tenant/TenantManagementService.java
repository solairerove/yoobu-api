package com.yoobu.api.tenant;

import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> getAllTenants() {
        return tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(tenant -> new TenantSummaryResponse(
                        tenant.getId(),
                        tenant.getSlug(),
                        tenant.getName(),
                        tenant.getType(),
                        tenant.isActive(),
                        tenant.getTimezone(),
                        tenant.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantDetailResponse getTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        Map<String, String> config = new LinkedHashMap<>();
        tenantConfigRepository.findByTenantId(tenantId)
                .forEach(entry -> config.put(entry.getKey(), entry.getValue()));

        return new TenantDetailResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getName(),
                tenant.getType(),
                tenant.isActive(),
                tenant.getTimezone(),
                tenant.getBotToken(),
                tenant.getOwnerTelegramId(),
                tenant.getCreatedAt(),
                config
        );
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
        tenant.setBotToken(request.botToken());
        tenant.setOwnerTelegramId(request.ownerTelegramId());
        tenant.setTimezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Asia/Ho_Chi_Minh");
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

        return new TenantSummaryResponse(
                savedTenant.getId(),
                savedTenant.getSlug(),
                savedTenant.getName(),
                savedTenant.getType(),
                savedTenant.isActive(),
                savedTenant.getTimezone(),
                savedTenant.getCreatedAt()
        );
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
}
