package ru.rutcampustrack.notification.contract.dto.push;

import org.springframework.hateoas.RepresentationModel;

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
