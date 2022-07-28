package com.example.demo.implementation.credential.provider;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientCertificateCredential;
import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.example.demo.identity.credential.TokenCredentialProvider;
import com.example.demo.identity.credential.TokenCredentialProviderOptions;
import com.example.demo.implementation.StaticAccessTokenCache;
import com.example.demo.implementation.credential.CacheableTokenCredential;
import com.example.demo.implementation.credential.adapter.CacheableClientCertificateCredential;
import com.example.demo.implementation.credential.adapter.CacheableClientSecretCredential;
import com.example.demo.implementation.credential.adapter.CacheableDefaultAzureCredential;
import com.example.demo.implementation.credential.adapter.CacheableManageIdentityCredential;
import com.example.demo.implementation.credential.adapter.CacheableUsernamePasswordCredential;
import org.springframework.util.StringUtils;

class DefaultTokenCredentialProvider implements TokenCredentialProvider {

    private TokenCredentialProviderOptions options;
    private final StaticAccessTokenCache cache = new StaticAccessTokenCache();

    DefaultTokenCredentialProvider() {
        this.options = new TokenCredentialProviderOptions();
    }

    DefaultTokenCredentialProvider(TokenCredentialProviderOptions options) {
        this.options = options;
    }

    @Override
    public TokenCredential get(TokenCredentialProviderOptions options) {
        if (options == null) {
            return new DefaultAzureCredentialBuilder().build();
        }

        TokenCredential tokenCredential = resolveTokenCredential(options);
        boolean cachedEnabled = options.isCachedEnabled();
        if (cachedEnabled) {
            return new CacheableTokenCredential(cache, options, tokenCredential);
        } else {
            return tokenCredential;
        }
    }

    private TokenCredential resolveTokenCredential(TokenCredentialProviderOptions options) {
        final String tenantId = options.getTenantId();
        final String clientId = options.getClientId();
        final boolean isClientIdSet = StringUtils.hasText(clientId);
        final String authorityHost = options.getAuthorityHost();
        if (StringUtils.hasText(tenantId)) {
            String clientSecret = options.getClientSecret();
            if (isClientIdSet && StringUtils.hasText(clientSecret)) {
                ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder().clientId(clientId)
                        .authorityHost(authorityHost)
                        .clientSecret(clientSecret)
                        .tenantId(tenantId)
                        .build();
                return new CacheableClientSecretCredential(options, clientSecretCredential);
            }

            String clientCertificatePath = options.getClientCertificatePath();
            if (StringUtils.hasText(clientCertificatePath)) {
                ClientCertificateCredentialBuilder builder = new ClientCertificateCredentialBuilder()
                        .authorityHost(authorityHost)
                        .tenantId(tenantId)
                        .clientId(clientId);

                if (StringUtils.hasText(options.getClientCertificatePassword())) {
                    builder.pfxCertificate(clientCertificatePath, options.getClientCertificatePassword());
                } else {
                    builder.pemCertificate(clientCertificatePath);
                }

                ClientCertificateCredential clientCertificateCredential = builder.build();
                return new CacheableClientCertificateCredential(options, clientCertificateCredential);
            }
        }

        if (isClientIdSet && StringUtils.hasText(options.getUsername())
                && StringUtils.hasText(options.getPassword())) {
            UsernamePasswordCredential usernamePasswordCredential = new UsernamePasswordCredentialBuilder().username(options.getUsername())
                    .authorityHost(authorityHost)
                    .password(options.getPassword())
                    .clientId(clientId)
                    .tenantId(tenantId)
                    .build();
            return new CacheableUsernamePasswordCredential(options, usernamePasswordCredential);
        }

        if (options.isManagedIdentityEnabled()) {
            ManagedIdentityCredentialBuilder builder = new ManagedIdentityCredentialBuilder();
            if (isClientIdSet) {
                builder.clientId(clientId);
            }
            ManagedIdentityCredential managedIdentityCredential = builder.build();
            return new CacheableManageIdentityCredential(options, managedIdentityCredential);
        }

        DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder()
                .authorityHost(authorityHost)
                .tenantId(tenantId)
                .managedIdentityClientId(clientId)
                .build();
        return new CacheableDefaultAzureCredential(options, defaultAzureCredential);
    }

    @Override
    public TokenCredential get() {
        return get(this.options);
    }
}