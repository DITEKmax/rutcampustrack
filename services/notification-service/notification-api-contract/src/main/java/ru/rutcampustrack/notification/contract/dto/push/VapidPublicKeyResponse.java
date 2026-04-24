package ru.rutcampustrack.notification.contract.dto.push;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

@Schema(description = "Публичный VAPID-ключ сервера для инициализации Web Push подписки на клиенте")
public class VapidPublicKeyResponse extends RepresentationModel<VapidPublicKeyResponse> {

    private String publicKey;

    public VapidPublicKeyResponse() {}

    public VapidPublicKeyResponse(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
