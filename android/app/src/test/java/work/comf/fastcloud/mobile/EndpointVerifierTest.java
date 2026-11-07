package work.comf.fastcloud.mobile;

import static org.junit.Assert.*;
import java.util.Properties;
import java.util.Base64;
import org.junit.Test;

public class EndpointVerifierTest {
  @Test public void oldOrConflictingAnnouncementsNeverUndoAMove() {
    assertFalse(EndpointVerifier.acceptSequence(3,"https://new.example",2,"https://old.example"));
    assertFalse(EndpointVerifier.acceptSequence(3,"https://new.example",3,"https://old.example"));
    assertTrue(EndpointVerifier.acceptSequence(3,"https://new.example",3,"https://new.example"));
    assertTrue(EndpointVerifier.acceptSequence(3,"https://new.example",4,"https://rollback.example"));
  }
  @Test public void theOfflineOwnerSignatureRejectsAChangedDestination() throws Exception {
    Properties fixture=new Properties();
    try(var input=getClass().getResourceAsStream("/service-endpoint-fixture.properties")){fixture.load(input);}
    byte[] key=Base64.getDecoder().decode(fixture.getProperty("publicKey"));
    byte[] payload=Base64.getDecoder().decode(fixture.getProperty("payload"));
    byte[] signature=Base64.getDecoder().decode(fixture.getProperty("signature"));
    EndpointVerifier.verify(key,payload,signature);
    payload[payload.length-2]^=1;
    try {EndpointVerifier.verify(key,payload,signature);fail("A modified announcement was accepted");} catch(java.security.SignatureException | java.io.IOException expected) {}
  }

  @Test public void destinationsCannotTargetThePhoneOrCarryCredentials() throws Exception {
    for(String url:new String[]{"http://example.com","https://127.0.0.1","https://192.168.1.1","https://localhost","https://example.com/path","https://u:p@example.com","https://example.com:8080"}){
      try{EndpointVerifier.origin(url);fail(url);}catch(java.io.IOException expected){}
    }
    assertEquals("https://80.47.238.200",EndpointVerifier.origin("https://80.47.238.200/"));
  }

  @Test public void theProductKeyMatchesTheInitialSignedWebsiteAnnouncement() throws Exception {
    Properties fixture=new Properties();
    try(var input=getClass().getResourceAsStream("/service-endpoint-initial.properties")){fixture.load(input);}
    java.io.File key=new java.io.File("src/main/res/raw/service_endpoint_public_key.txt");
    assertTrue("Missing product public key",key.isFile());
    try(var input=new java.io.BufferedReader(new java.io.FileReader(key))){
      EndpointVerifier.verify(Base64.getDecoder().decode(input.readLine().trim()),Base64.getDecoder().decode(fixture.getProperty("payload")),Base64.getDecoder().decode(fixture.getProperty("signature")));
    }
  }
}
