package org.lantern.privacy; 

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.commons.codec.binary.Base64;
import org.lantern.LanternConstants;
import org.lantern.event.Events;
import org.lantern.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import com.mcdermottroe.apple.OSXKeychain;
import com.mcdermottroe.apple.OSXKeychainException;

/**
 * MacLocalCipherProvider
 *
 * This is a LocalCipherProvider that uses 
 * the OS X KeyChain to store a local key
 * used to encrypt/decrypt local data.
 *
 */
@Singleton
public class MacLocalCipherProvider extends AbstractAESLocalCipherProvider {

    private static final String SERVICE_NAME = "Lantern Local Privacy";
    private static final String ACCOUNT_NAME = "lantern";

    private final Logger log = LoggerFactory.getLogger(getClass());

    /*
    public MacLocalCipherProvider(final File validatorFile, final File cipherParamsFile) {
        super(validatorFile, cipherParamsFile);
    }
    */

    @Override
    byte[] loadKeyData() throws IOException, GeneralSecurityException {
        try {
            OSXKeychain keychain = OSXKeychain.getInstance();
            final Base64 base64 = new Base64();
            final String encodedKey = 
                keychain.findGenericPassword(SERVICE_NAME, ACCOUNT_NAME);
            return base64.decode(encodedKey.getBytes(LanternConstants.UTF8));
        } catch (final OSXKeychainException e) {
            log.error("OSX keychain error?", e);
            Events.asyncEventBus().post(new MessageEvent("Keychain error", 
                "Sorry, but Lantern experienced a problem reading Lantern " +
                "data from your keychain. Try resetting Lantern, deleting " +
                "the Lantern entry in your keychain, or even re-installing."));
            throw new GeneralSecurityException("Keychain error?", e);
        }
    }

    @Override
    void storeKeyData(final byte[] key) throws IOException, GeneralSecurityException {
        final Base64 base64 = new Base64();
        final byte [] encodedKey = base64.encode(key);
        try {
            OSXKeychain keychain = OSXKeychain.getInstance();
            String keyString = new String(encodedKey, "UTF-8");
            try {
                keychain.modifyGenericPassword(SERVICE_NAME, ACCOUNT_NAME, keyString);
                log.debug("Replaced old lantern keychain entry.");
            } catch (final OSXKeychainException e) {
                // not found, add 
                keychain.addGenericPassword(SERVICE_NAME, ACCOUNT_NAME, keyString);
                log.debug("Created new lantern keychain entry.");
            }
        } catch (final OSXKeychainException e) {
            log.error("Error adding to keychain?", e);
            Events.asyncEventBus().post(new MessageEvent("Keychain error", 
                "Sorry, but there was an error writing to your keychain. " +
                "Try resetting Lantern, deleting the Lantern entry in your " +
                "keychain, or even re-installing."));
            throw new GeneralSecurityException("Keychain error?", e);
        } finally {
            zeroFill(encodedKey);
        }
    }
}