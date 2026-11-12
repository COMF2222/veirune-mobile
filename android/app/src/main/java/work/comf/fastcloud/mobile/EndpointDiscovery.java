package work.comf.fastcloud.mobile;

import android.content.Context;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class EndpointDiscovery {
  private static final String BOOK = "https://veirune.comf.workers.dev/service.json";
  private static volatile long attempted;
  private static volatile JSONObject current;
  private static boolean loaded;
  private static final Object FETCH = new Object();
  private static final java.util.concurrent.atomic.AtomicBoolean scheduled=new java.util.concurrent.atomic.AtomicBoolean();
  private static final java.util.concurrent.ExecutorService background=java.util.concurrent.Executors.newSingleThreadExecutor();

  static void schedule(Context context) {
    long last=attempted;
    if(last!=0 && android.os.SystemClock.elapsedRealtime()-last<45000)return;
    if (!scheduled.compareAndSet(false,true)) return;
    background.execute(() -> {try {refresh(context,false);} finally {scheduled.set(false);}});
  }

  private static JSONObject verify(Context context, String envelope) throws Exception {
    if (envelope.length() > 8192) throw new IOException("Endpoint announcement too large");
    JSONObject value = new JSONObject(envelope);
    if (value.optInt("schema") != 1) throw new IOException("Unsupported endpoint announcement");
    byte[] payload = Base64.decode(value.getString("payload"), Base64.DEFAULT);
    byte[] signature = Base64.decode(value.getString("signature"), Base64.DEFAULT);
    byte[] publicKey;
    try (var input = context.getResources().openRawResource(R.raw.service_endpoint_public_key)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024]; int count;
      while ((count = input.read(buffer)) != -1) bytes.write(buffer,0,count);
      publicKey = Base64.decode(new String(bytes.toByteArray(),StandardCharsets.US_ASCII).trim(), Base64.DEFAULT);
    }
    EndpointVerifier.verify(publicKey,payload,signature);
    JSONObject result = new JSONObject(new String(payload,StandardCharsets.UTF_8));
    if (!"veirune-main".equals(result.optString("service")) || result.optLong("serial") <= 0 || result.optLong("serial") >= (1L<<53)) throw new IOException("Invalid endpoint identity");
    result.put("origin",EndpointVerifier.origin(result.getString("origin")));
    return result;
  }

  private static synchronized void load(Context context) {
    if (loaded) return;
    loaded = true;
    String cached = context.getSharedPreferences("mobile",0).getString("endpoint-announcement",null);
    if (cached != null) try { current = verify(context,cached); } catch (Exception ignored) {}
  }

  static String origin(Context context, String fallback) {
    load(context);
    return context.getSharedPreferences("mobile",0).getBoolean("managed-endpoint",ServerMigration.CURRENT.equals(fallback)) && current != null
      ? current.optString("origin",fallback) : fallback;
  }

  static boolean known(Context context, String origin) {
    load(context);
    if (ServerMigration.CURRENT.equals(origin)) return true;
    JSONObject entry=current;
    if (entry==null) return false;
    if (origin.equals(entry.optString("origin"))) return true;
    var previous=entry.optJSONArray("previous");
    if(previous!=null)for(int index=0;index<previous.length();index++)if(origin.equals(previous.optString(index)))return true;
    return false;
  }

  static boolean refresh(Context context, boolean force) {
    synchronized (FETCH) {
    var preferences = context.getSharedPreferences("mobile",0);
    if (!force && !preferences.getBoolean("managed-endpoint",ServerMigration.CURRENT.equals(preferences.getString("server",ServerMigration.CURRENT)))) return false;
    load(context);
    long now = android.os.SystemClock.elapsedRealtime();
    if (!force && attempted != 0 && now-attempted<45000) return false;
    attempted = now;
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection)new URL(BOOK).openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setUseCaches(false);connection.setRequestProperty("Cache-Control","no-cache");
      connection.setConnectTimeout(2500);connection.setReadTimeout(2500);
      if (connection.getResponseCode()!=200) return false;
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (var input=connection.getInputStream()) {
        byte[] buffer=new byte[2048];int count;
        while ((count=input.read(buffer))!=-1) {if(output.size()+count>8192)throw new IOException("Endpoint announcement too large");output.write(buffer,0,count);}
      }
      String envelope = new String(output.toByteArray(),StandardCharsets.UTF_8);
      JSONObject next = verify(context,envelope);
      if (current != null && !EndpointVerifier.acceptSequence(current.getLong("serial"),current.getString("origin"),next.getLong("serial"),next.getString("origin"))) return false;
      if (current != null && next.getLong("serial")==current.getLong("serial")) return false;
      boolean changed = !next.getString("origin").equals(current==null ? ServerMigration.CURRENT : current.getString("origin"));
      if (!preferences.edit().putString("endpoint-announcement",envelope).commit()) return false;
      current=next;
      preferences.edit().putLong("endpoint-revision",next.getLong("serial")).apply();
      return changed;
    } catch (Exception ignored) {return false;}
    finally {if(connection!=null)connection.disconnect();}
    }
  }
}
