import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.zz.gmhelper.BCECUtil;
import org.zz.gmhelper.SM2Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Base64;

public class GenerateSm2Signature {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: GenerateSm2Signature <conditionDescription> <publicKeyDerPath> <privateKeyDerPath>");
            System.exit(2);
        }
        Security.addProvider(new BouncyCastleProvider());
        String conditionDescription = args[0];
        byte[] publicKeyDer = Files.readAllBytes(Paths.get(args[1]));
        byte[] privateKeyDer = Files.readAllBytes(Paths.get(args[2]));
        BCECPrivateKey privateKey = BCECUtil.convertPKCS8ToECPrivateKey(privateKeyDer);
        String dPk = Base64.getEncoder().encodeToString(publicKeyDer);
        String signature = Base64.getEncoder().encodeToString(
                SM2Util.sign(privateKey, conditionDescription.getBytes(StandardCharsets.UTF_8))
        );
        System.out.println("{\"dPk\":\"" + dPk + "\",\"signature\":\"" + signature + "\"}");
    }
}
