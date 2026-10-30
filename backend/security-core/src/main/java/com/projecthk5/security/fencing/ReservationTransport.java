package com.projecthk5.security.fencing;

import com.projecthk5.security.BoundedHttpResponse;
import com.projecthk5.security.InternalPairVerifier;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

final class ReservationTransport {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Supplier<String> credential;
    ReservationTransport(Supplier<String> credential){this.credential=Objects.requireNonNull(credential);}
    static void endpoint(URI uri,Set<String> paths){boolean loopback=Set.of("localhost","127.0.0.1","[::1]").contains(Objects.toString(uri.getHost(),""));if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!("https".equals(uri.getScheme())||"http".equals(uri.getScheme())&&loopback)||!paths.contains(uri.getRawPath()))throw new IllegalArgumentException("Explicit private reservation endpoint with TLS required");}
    String post(URI endpoint,String body,String bearer){try{String secret=credential.get();new InternalPairVerifier.Credentials(secret,null,null);var builder=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").header("Accept","application/json").header("Cache-Control","no-store").header("X-HK5-Service-Credential",secret);if(bearer!=null){if(bearer.isBlank())throw new IllegalArgumentException();builder.header("Authorization","Bearer "+bearer);}var response=BoundedHttpResponse.send(http,builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),16384);int status=response.statusCode();if(status==401||status==403||status==409)throw new ReservationRpcClient.Failure(status);if(status!=200||!response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].trim().equalsIgnoreCase("application/json")||!Arrays.stream(response.headers().allValues("Cache-Control").stream().collect(java.util.stream.Collectors.joining(",")).split(",")).anyMatch(v->v.trim().equalsIgnoreCase("no-store")))throw new ReservationRpcClient.Failure(503);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body())).toString();}catch(ReservationRpcClient.Failure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ReservationRpcClient.Failure(503);}catch(Exception e){throw new ReservationRpcClient.Failure(503);}}
}
