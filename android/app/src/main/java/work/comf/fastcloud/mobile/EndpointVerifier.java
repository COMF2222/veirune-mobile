package work.comf.fastcloud.mobile;

import java.net.URI;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

final class EndpointVerifier {
  static boolean acceptSequence(long previous, String before, long next, String after) {
    return next>=previous && (next!=previous || before.equals(after));
  }
  static void verify(byte[] publicKey, byte[] payload, byte[] signature) throws Exception {
    Signature verifier = Signature.getInstance("SHA256withECDSA");
    verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKey)));
    verifier.update(payload);
    if (!verifier.verify(signature)) throw new java.io.IOException("Invalid endpoint signature");
  }

  static String origin(String value) throws Exception {
    URI uri = new URI(value);
    String host = uri.getHost();
    if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
      || (uri.getPort() != -1 && uri.getPort() != 443) || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
      || host.equalsIgnoreCase("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) throw new java.io.IOException("Invalid public endpoint");
    if (host.matches("[0-9.]+") || host.contains(":")) {
      java.net.InetAddress address = java.net.InetAddress.getByName(host);
      if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) throw new java.io.IOException("Local endpoint rejected");
    }
    return "https://" + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT);
  }
}
