package net.trajano.ms.common.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.nimbusds.jose.jwk.JWKSet;

import net.trajano.ms.common.beans.CryptoProvider;
import net.trajano.ms.common.beans.JwksProvider;
import net.trajano.ms.common.beans.TokenGenerator;
import net.trajano.ms.sample.JwksResource;

public class CacheCreationTest {

    private final CryptoProvider cryptoProvider = new CryptoProvider();

    /**
     * Verify that the concurrent cache manager returns something.
     */
    @Test
    public void springConcurrentMapCacheManagerTest() throws Exception {

        final ConcurrentMapCacheManager cm = new ConcurrentMapCacheManager("aaaKs_cache");
        assertNotNull(cm.getCache("aaaKs_cache"));

        final JwksProvider jwksProvider = new JwksProvider();
        final TokenGenerator tokenGenerator = new TokenGenerator();
        tokenGenerator.setRandom(cryptoProvider.secureRandom());
        jwksProvider.setTokenGenerator(tokenGenerator);
        jwksProvider.setJwksCache(cm.getCache("aaaKs_cache"));
        jwksProvider.setKeyPairGenerator(cryptoProvider.keyPairGenerator());
        jwksProvider.setRandom(cryptoProvider.secureRandom());

        jwksProvider.init();

    }

    @Test
    public void testProvider() throws Exception {

        final JwksProvider jwksProvider = new JwksProvider();
        final TokenGenerator tokenGenerator = new TokenGenerator();
        tokenGenerator.setRandom(cryptoProvider.secureRandom());
        jwksProvider.setTokenGenerator(tokenGenerator);
        jwksProvider.setKeyPairGenerator(cryptoProvider.keyPairGenerator());
        jwksProvider.setRandom(cryptoProvider.secureRandom());
        jwksProvider.init();
        final JWKSet keySet = jwksProvider.getKeySet();
        assertNotNull(keySet);
        assertEquals(JwksProvider.MIN_NUMBER_OF_KEYS, keySet.getKeys().size());
    }

    @Test
    public void testResource() throws Exception {

        final JwksProvider jwksProvider = new JwksProvider();
        final TokenGenerator tokenGenerator = new TokenGenerator();
        tokenGenerator.setRandom(cryptoProvider.secureRandom());
        jwksProvider.setTokenGenerator(tokenGenerator);
        jwksProvider.setKeyPairGenerator(cryptoProvider.keyPairGenerator());
        jwksProvider.setRandom(cryptoProvider.secureRandom());
        jwksProvider.init();

        final JwksResource jwksResource = new JwksResource();
        jwksResource.setJwksProvider(jwksProvider);
        System.out.println(jwksResource.getPublicKeySet());
        //        @SuppressWarnings("unchecked")
        //        final List<JsonWebKey> keys = (List<JsonWebKey>) jwksResource.getPublicKeySet().getEntity();
        //        assertEquals(JwksProvider.MIN_NUMBER_OF_KEYS, keys.size());
    }

}
