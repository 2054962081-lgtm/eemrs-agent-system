package com.lilin.client.crypto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;

@AllArgsConstructor
@NoArgsConstructor
public class SM2ClientKey {
    @Getter
    private BCECPublicKey publicKey;
    @Getter
    private BCECPrivateKey privateKey;
}
