package com.liu.eemrsserver.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;

@AllArgsConstructor
@NoArgsConstructor
public class SMServerKey {
    @Getter
    private BCECPublicKey publicKey;
    @Getter
    private BCECPrivateKey privateKey;
    @Getter
    private byte[] sm4Key;
    @Getter
    private byte[] opeKey;
}
