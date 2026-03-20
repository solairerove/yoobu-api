package com.yoobu.api.admin.panel;

import com.yoobu.api.catalog.ServiceStatus;
import jakarta.validation.constraints.NotNull;

public class ServiceStatusForm {

    @NotNull
    private ServiceStatus status;

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }
}
