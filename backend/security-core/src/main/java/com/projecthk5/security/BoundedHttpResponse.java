package com.projecthk5.security;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
/** Shared capped transport with a whole-response deadline, including body completion. */
public final class BoundedHttpResponse {
    private BoundedHttpResponse(){}
    public static HttpResponse<byte[]> send(HttpClient client,HttpRequest request,int limit) throws Exception {
        if(limit<1 || limit>1048576)throw new IllegalArgumentException("Invalid response cap");
        var pending=client.sendAsync(request,info->new Body(limit));
        try{return pending.get(3,TimeUnit.SECONDS);}catch(Exception failure){pending.cancel(true);throw failure;}
    }
    private static final class Body implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private final int limit;private Flow.Subscription subscription;
        Body(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(Long.MAX_VALUE);}
        public void onNext(List<java.nio.ByteBuffer> buffers){for(var buffer:buffers){
            if(bytes.size()+buffer.remaining()>limit){subscription.cancel();result.completeExceptionally(new IllegalStateException("Response too large"));return;}
            byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);
        }
}
        public void onError(Throwable failure){result.completeExceptionally(failure);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
