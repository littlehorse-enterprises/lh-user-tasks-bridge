package io.littlehorse.usertasks.configurations;

import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.URI;

@Data
@AllArgsConstructor
public class CustomIdentityProviderProperties {
    private URI iss;
    private String usernameClaim;
    private IdentityProviderVendor vendor;
}
