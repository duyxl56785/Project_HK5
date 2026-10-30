package com.projecthk5.productservice.authority;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.projecthk5.security.*;
import com.projecthk5.security.fencing.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ProductReceiptMediaTypeTest {
    static DataSource database;AnnotationConfigWebApplicationContext context;MockMvc mvc;
    String key="test-only-product-auth-receipt-key-0123456789",body;
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(ProductWriteReceiptConfiguration.class)
    static class Host {@Bean DataSource source(){return database;}}
    @BeforeEach void start(){
        database=mock(DataSource.class);context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        var values=new HashMap<String,String>();for(String pair:PairCredentialManifest.PAIRS)values.put(pair+"SECRET","test-only-distinct-pair-key-0123456789-"+pair);
        values.put("HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_SECRET",key);
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,"hk5.product.receipts.enabled=true","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_SECRET="+key,
            PairCredentialManifest.PROPERTY+"="+PairCredentialManifest.provision(values::get));
        context.register(Host.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
        body=ReservationWire.request("receipt",UUID.randomUUID(),new WriteIntent(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"test-token",WriteIntent.Mode.MANAGE,0,0,0,"a".repeat(64)),1L);
    }
    @AfterEach void close(){context.close();}
    @Test void validPairWithWrongOrMissingMediaTypeCannotReachDatabase() throws Exception {
        mvc.perform(post(ProductWriteReceiptConfiguration.AUTH_PATH).header("X-HK5-Service-Credential",key).contentType("text/plain").content(body)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(ProductWriteReceiptConfiguration.AUTH_PATH).header("X-HK5-Service-Credential",key).content(body)).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(database);
    }
    @Test void credentialRejectionPrecedesMediaTypeAndNeverTouchesDatabase() throws Exception {
        mvc.perform(post(ProductWriteReceiptConfiguration.AUTH_PATH).header("X-HK5-Service-Credential","wrong-pair-credential-0123456789").contentType("text/plain").content(body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(database);
    }
}
