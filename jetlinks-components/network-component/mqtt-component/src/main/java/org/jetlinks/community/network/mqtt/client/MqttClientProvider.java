package org.jetlinks.community.network.mqtt.client;

import com.alibaba.fastjson.JSONObject;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.core.metadata.ConfigMetadata;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.BooleanType;
import org.jetlinks.core.metadata.types.IntType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.community.network.*;
import org.jetlinks.community.network.security.CertificateManager;
import org.jetlinks.community.network.security.VertxKeyCertTrustOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Component
@Slf4j
public class MqttClientProvider implements NetworkProvider<MqttClientProperties> {

    private final Vertx vertx;

    private final CertificateManager certificateManager;

    public MqttClientProvider(CertificateManager certificateManager, Vertx vertx) {
        this.vertx = vertx;
        this.certificateManager = certificateManager;
    }

    @Nonnull
    @Override
    public NetworkType getType() {
        return DefaultNetworkType.MQTT_CLIENT;
    }

    @Nonnull
    @Override
    public VertxMqttClient createNetwork(@Nonnull MqttClientProperties properties) {
        VertxMqttClient mqttClient = new VertxMqttClient(properties.getId());
        initMqttClient(mqttClient, properties);
        return mqttClient;
    }

    @Override
    public void reload(@Nonnull Network network, @Nonnull MqttClientProperties properties) {
       VertxMqttClient mqttClient = ((VertxMqttClient) network);
        if (mqttClient.isLoading()) {
            return;
        }
        initMqttClient(mqttClient, properties);
    }

    public void initMqttClient(VertxMqttClient mqttClient, MqttClientProperties properties) {
        mqttClient.setLoading(true);
        MqttClient client = MqttClient.create(vertx, properties.getOptions());
        mqttClient.setClient(client);
        client.connect(properties.getPort(), properties.getHost(), result -> {
            mqttClient.setLoading(false);
            if (!result.succeeded()) {
                log.warn("connect mqtt [{}] error", properties.getId(), result.cause());
            } else {
                log.debug("connect mqtt [{}] success", properties.getId());
            }
        });
    }

    @Nullable
    @Override
    public ConfigMetadata getConfigMetadata() {
        return new DefaultConfigMetadata()
            .add("id", "id", "", new StringType())
            .add("instance", "服务实例数量（线程数）", "", new IntType())
            .add("certId", "证书id", "", new StringType())
            .add("ssl", "是否开启ssl", "", new BooleanType())
            .add("options.port", "MQTT服务设置", "", new IntType());
    }

    @Nonnull
    @Override
    public Mono<MqttClientProperties> createConfig(@Nonnull NetworkProperties properties) {
        return Mono.defer(() -> {
            MqttClientProperties config = FastBeanCopier.copy(properties.getConfigurations(), new MqttClientProperties());
            config.setId(properties.getId());
            config.setOptions(new JSONObject(properties.getConfigurations()).toJavaObject(MqttClientOptions.class));

            config.getOptions().setClientId(config.getClientId());
            config.getOptions().setPassword(config.getPassword());
            config.getOptions().setUsername(config.getUsername());

            if (config.isSsl()) {
                config.getOptions().setSsl(true);
                return certificateManager.getCertificate(config.getCertId())
                    .map(VertxKeyCertTrustOptions::new)
                    .doOnNext(config.getOptions()::setKeyCertOptions)
                    .doOnNext(config.getOptions()::setTrustOptions)
                    .thenReturn(config);
            }
            return Mono.just(config);
        });
    }
}
