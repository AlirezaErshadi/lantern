package org.lantern;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.lantern.http.HttpUtils;
import org.lantern.http.LanternApi;
import org.lantern.privacy.InvalidKeyException;
import org.lantern.privacy.LocalCipherProvider;
import org.lantern.state.Model;
import org.lantern.state.Model.Run;
import org.lantern.state.ModelIo;
import org.lantern.state.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Default implementation of the Lantern API.
 */
@Singleton
public class DefaultLanternApi implements LanternApi {
    
    
    /**
     * Enumeration of calls to the Lantern API.
     */
    private enum LanternApiCall {
        SIGNIN,
        SIGNOUT,
        APPLYAUTOPROXY,
        ADDTOWHITELIST,
        REMOVEFROMWHITELIST,
        ADDTRUSTEDPEER,
        REMOVETRUSTEDPEER,
        RESET,
        ROSTER,
        CONTACT,
        WHITELIST,
        SETLOCALPASSWORD,
        UNLOCK,
        ERROR,
        INVITE,
        SUBSCRIBED,
        UNSUBSCRIBED,
        STATE
    }
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final XmppHandler xmppHandler;
    //private final ModelChangeImplementor modelChangeImplementor;
    private final LocalCipherProvider lcp;
    private final Proxifier proxifier;
    private final Model model;
    private final ModelUtils modelUtils;
    private final ModelIo modelIo;

    @Inject
    public DefaultLanternApi(final XmppHandler xmppHandler,
        //final ModelChangeImplementor modelChangeImplementor,
        final LocalCipherProvider lcp,
        final Proxifier proxifier, final Model model, 
        final ModelUtils modelUtils, final ModelIo modelIo) {
        this.xmppHandler = xmppHandler;
        //this.modelChangeImplementor = modelChangeImplementor;
        this.lcp = lcp;
        this.proxifier = proxifier;
        this.model = model;
        this.modelUtils = modelUtils;
        this.modelIo = modelIo;
    }
    
    @Override
    public void processCall(final HttpServletRequest req, 
        final HttpServletResponse resp) {
        final String uri = req.getRequestURI();
        final String id = StringUtils.substringAfter(uri, "/api/");
        final LanternApiCall call = LanternApiCall.valueOf(id.toUpperCase());
        log.debug("Got API call {} for full URI: "+uri, call);
        switch (call) {
        /*
        case SIGNIN:
            handleSignin(req, resp);
            break;
            */
        case SIGNOUT:
            handleSignout(resp);
            break;
        case APPLYAUTOPROXY:
            handleAutoproxy(resp);
            break;
        case RESET:
            handleReset(resp);
            modelIo.write();
            break;
        case ADDTOWHITELIST:
            this.model.getSettings().getWhitelist().addEntry(req.getParameter("site"));
            proxifier.refresh();
            handleWhitelist(resp);
            this.modelIo.write();
            break;
        case REMOVEFROMWHITELIST:
            this.model.getSettings().getWhitelist().removeEntry(req.getParameter("site"));
            proxifier.refresh();
            handleWhitelist(resp);
            this.modelIo.write();
            break;
            /*
        case ADDTRUSTEDPEER:
            // TODO: Add data validation.
            LanternHub.getTrustedContactsManager().addTrustedContact(
                req.getParameter("email"));
            handleRoster(resp);
            break;
        case REMOVETRUSTEDPEER:
            // TODO: Add data validation.
            LanternHub.getTrustedContactsManager().removeTrustedContact(
                req.getParameter("email"));
            handleRoster(resp);
            break;
            */
        case ROSTER:
            handleRoster(resp);
            break;
        case CONTACT:
            //handleContactForm(req, resp);
            break;
        case WHITELIST:
            handleWhitelist(resp);
            break;
        case UNLOCK:
            handleUnlock(req, resp);
            break;
        case ERROR:
            handleError(req, resp);
            break;
        case INVITE:
            handleInvite(req, resp);
            break;
        case SUBSCRIBED:
            handleSubscribed(req, resp);
            break;
        case UNSUBSCRIBED:
            handleUnsubscribed(req, resp);
            break;
        case STATE:
            handleState(req, resp);
            break;
        }
    }
    
    private void handleState(final HttpServletRequest req,
        final HttpServletResponse resp) {
        log.debug("Got state request");
        returnSettings(resp);
    }

    private void handleSubscribed(final HttpServletRequest req,
        final HttpServletResponse resp) {
        final Map<String, String> params = HttpUtils.toParamMap(req);
        final String jid = params.remove("jid");
        if (StringUtils.isBlank(jid)) {
            sendClientError(resp, "No jid argument provided");
            return;
        }
        this.xmppHandler.subscribed(jid);
        
        // We also automatically subscribe to them in turn so we know about
        // their presence.
        this.xmppHandler.subscribe(jid);
        returnSettings(resp);
    }
    
    private void handleUnsubscribed(final HttpServletRequest req,
        final HttpServletResponse resp) {
        final Map<String, String> params = HttpUtils.toParamMap(req);
        final String jid = params.remove("jid");
        if (StringUtils.isBlank(jid)) {
            sendClientError(resp, "No jid argument provided");
            return;
        }
        this.xmppHandler.unsubscribed(jid);
        returnSettings(resp);
    }

    private void handleInvite(final HttpServletRequest req, 
        final HttpServletResponse resp) {
        final Map<String, String> params = HttpUtils.toParamMap(req);
        final String email = params.remove("email");
        if (StringUtils.isBlank(email)) {
            sendClientError(resp, "No email argument provided");
            return;
        }
        this.xmppHandler.sendInvite(email);
        returnSettings(resp);
    }

    private void handleError(final HttpServletRequest req, 
        final HttpServletResponse resp) {
        final String msg = req.getParameter("msg");
        if (StringUtils.isNotBlank(msg)) {
            log.error(msg);
            ok(resp);
        } else {
            sendClientError(resp, "No msg argument in error API call");
        }
    }

    /*
    private void handleSignin(final HttpServletRequest req, 
        final HttpServletResponse resp) {
        log.debug("Signing in...");
        final Settings set = LanternHub.settings();
        this.xmppHandler.disconnect();
        final Map<String, String> params = HttpUtils.toParamMap(req);

        final String rawEmail = params.remove("email");
        if (StringUtils.isBlank(rawEmail)) {
            sendClientError(resp, "No email address provided");
            return;
        }
        final String email;
        if (!rawEmail.contains("@")) {
            email = rawEmail + "@gmail.com";
        } else {
            email = rawEmail;
        }
        // important: keep in this order, changing email 
        // discards user settings associated with the old 
        // address (eg saved password if desired) -- 
        // the saved password is purposely not examined until
        // this setting has been changed (or not changed)
        changeSetting(resp, "email", email, false, false);
        
        String pass = params.remove("password");
        if (StringUtils.isBlank(pass) && set.isSavePassword()) {
            pass = set.getStoredPassword();
            if (StringUtils.isBlank(pass)) {
                sendClientError(resp, "No password given and no password stored");
                return;
            }
        }
        changeSetting(resp, "password", pass, false, false);
        log.debug("Signing in with password..");
        
        // We write to disk to make sure Lantern's considered configured for
        // the subsequent connect call.
        LanternHub.settingsIo().write();
        try {
            this.xmppHandler.connect();
            if (this.model.isSetupComplete()) {
                // We automatically start proxying upon connect if the 
                // user's settings say they're in get mode and to use the 
                // system proxy.
                
                // TODO: We actually should not start proxying here since the
                // user doesn't necessarily have proxies to connect to. We
                // should only start proxying upon connecting to proxies!!
                proxifier.startProxying();
            }
            returnSettings(resp);
        } catch (final IOException e) {
            sendServerError(resp, "Could not connect: "+e.getMessage());
        } catch (final Proxifier.ProxyConfigurationError e) {
            log.error("Proxy configuration failed: {}", e);
            sendServerError(resp, "Proxy configuration failed");
        } catch (final CredentialException e) {
            log.info("CredentialException, clearing password");
            changeSetting(resp, "password", "", false, false);
            sendError(resp, HttpStatus.SC_UNAUTHORIZED, e.getMessage());
        } catch (final NotInClosedBetaException e) {
            log.info("NotInClosedBetaException, clearing password");
            changeSetting(resp, "password", "", false, false);
            sendError(resp, HttpStatus.SC_FORBIDDEN, e.getMessage());
        }
    }
    */


    private void handleSignout(final HttpServletResponse resp) {
        log.info("Signing out");
        signout();
        returnSettings(resp);
    }

    private void handleAutoproxy(final HttpServletResponse resp) {
        try {
            if (modelUtils.shouldProxy()) {
                proxifier.startProxying();
            }
            else {
                proxifier.stopProxying();
            }
        }
        catch (final Proxifier.ProxyConfigurationCancelled e) {
            sendServerError(resp, "Automatic proxy configuration cancelled.");
        }
        catch (final Proxifier.ProxyConfigurationError e) {
            sendServerError(resp, "Failed to configure system proxy.");
        }
    }

    private void handleReset(final HttpServletResponse resp) {
        
        //try {
        //    this.xmppHandler.clearProxies();
          //  signout();
            //this.model.getSettings().setInClosedBeta(new HashSet<String>());
            //LanternHub.destructiveFullReset();
            
            returnSettings(resp);
        //} catch (final IOException e) {
        //    sendServerError(resp, "Error resetting settings: "+
        //       e.getMessage());
        //}
    }
    
    private void signout() {

        // We stop proxying outside of any user settings since if we're
        // not logged in there's no sense in proxying. Could theoretically
        // use cached proxies, but definitely no peer proxies would work.
        try {
            proxifier.stopProxying();
        } catch (Proxifier.ProxyConfigurationError e) {
            log.error("failed to stop proxying: {}", e);
        }
        this.xmppHandler.disconnect();
        
        // clear user specific settings 
        // roster, trusted contacts, saved password
        
        
    }
    
    /** 
     * unlock 
     */
    private void handleUnlock(final HttpServletRequest req, 
                              final HttpServletResponse resp) {
        //final LocalCipherProvider lcp = LanternHub.localCipherProvider();
        if (lcp.isInitialized() == false) {
            sendClientError(resp, "Local password has not been set, must set first.");
            return;
        }
        if (!lcp.requiresAdditionalUserInput()) {
            sendClientError(resp, "Local cipher does not require password.");
            return;
        }
        final String password = req.getParameter("password");
        if (StringUtils.isBlank(password)) {
            sendClientError(resp, "Password cannot be blank");
            return;
        }
        try {
            // give it the password, *not intialization*
            lcp.feedUserInput(password.toCharArray(), false);
            /*
            // immediately "unlock" the settings
            LanternHub.resetSettings(true);
            SettingsState.State ss = LanternHub.settings().getSettings().getState();
            if (ss != SettingsState.State.SET) {
                log.error("Settings did not unlock, state is {}", ss);
                // still return the settings in this case so that the frontend
                // can deal with the state.
            }
            */
            returnSettings(resp);

        } catch (final InvalidKeyException e) {
            // bad password 
            sendClientError(resp, "Invalid password");
            return;
        } catch (final GeneralSecurityException e) {
            sendServerError(resp, "Error unlocking settings");
            log.error("Unexpected error unlocking settings: {}", e);
        } catch (final IOException e) {
            sendServerError(resp, "Error unlocking settings.");
            log.error("Unexpected error unlocking settings: {}", e);
        }
    }

    private void returnSettings(final HttpServletResponse resp) {
        final String json = LanternUtils.jsonify(model, Model.Run.class);
        returnJson(resp, json);
    }

    private void handleWhitelist(final HttpServletResponse resp) {
        returnJson(resp, this.model.getSettings().getWhitelist());
    }


    private void handleRoster(final HttpServletResponse resp) {
        log.info("Processing roster call.");
        if (!this.xmppHandler.isLoggedIn()) {
            sendClientError(resp, "Not logged in!");
            return;
        }
        // TODO: We need to block until the roster is actually loaded here.
        
        //returnJson(resp, this.xmppHandler.getRoster());
    }

    private void returnJson(final HttpServletResponse resp, final Object obj) {
        final String json = LanternUtils.jsonify(obj, Run.class);
        returnJson(resp, json);
    }
    
    private void returnJson(final HttpServletResponse resp, final String json) {
        final byte[] body;
        try {
            body = json.getBytes("UTF-8");
        } catch (final UnsupportedEncodingException e) {
            log.error("We need UTF-8");
            return;
        }
        log.info("Returning json...");
        resp.setStatus(HttpStatus.SC_OK);
        resp.setContentLength(body.length);
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache,no-store,max-age=0");
        try {
            resp.getOutputStream().write(body);
            resp.getOutputStream().flush();
        } catch (final IOException e) {
            log.info("Could not write response", e);
        }
    }
    
    private void ok(final HttpServletResponse resp) {
        log.info("Returning json...");
        resp.setStatus(HttpStatus.SC_OK);
        resp.setContentLength(0);
        resp.setHeader("Cache-Control", "no-cache,no-store,max-age=0");
        try {
            resp.getOutputStream().close();
        } catch (final IOException e) {
            log.info("Could not write response", e);
        }
    }
    
    private void sendClientError(final HttpServletResponse resp, 
        final String msg) {
        sendError(resp, HttpStatus.SC_BAD_REQUEST, msg);
    }
    
    private void sendServerError(final HttpServletResponse resp, 
        final String msg) {
        sendError(resp, HttpStatus.SC_INTERNAL_SERVER_ERROR, msg);
    }
    
    private void sendServerError(final Exception e, 
        final HttpServletResponse resp, final boolean sendErrors) {
        log.info("Caught exception", e);
        if (sendErrors) {
            sendError(resp, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void sendError(final HttpServletResponse resp, final int errorCode, 
        final String msg) {
        try {
            resp.sendError(errorCode, msg);
        } catch (final IOException e) {
            log.info("Could not send response", e);
        }
    }

    @Override
    public void changeSetting(final HttpServletRequest req, 
        final HttpServletResponse resp) {
        final Map<String, String> params = HttpUtils.toParamMap(req);
        changeSetting(resp, params);

    }
    
    private void changeSetting(final HttpServletResponse resp,
        final Map<String, String> params) {
        if (params.isEmpty()) {
            sendClientError(resp, "You must set a setting");
            return;
        }
        final Entry<String, String> keyVal = params.entrySet().iterator().next();
        log.debug("Got keyval: {}", keyVal);
        final String key = keyVal.getKey();
        final String val = keyVal.getValue();
        changeSetting(resp, key, val);
    }
    
    private void changeSetting(final HttpServletResponse resp, final String key, 
        final String val) {
        changeSetting(resp, key, val, true);
    }
    
    private void changeSetting(final HttpServletResponse resp, final String key, 
            final String val, final boolean determineType) {
        changeSetting(resp, key, val, determineType, true);
    }

    private void changeSetting(final HttpServletResponse resp, final String key, 
        final String val, final boolean determineType, final boolean sync) {
        /*
        setProperty(this.modelChangeImplementor, key, val, false, 
            resp, determineType);
        setProperty(LanternHub.settings(), key, val, true, resp, determineType);
        resp.setStatus(HttpStatus.SC_OK);
        if (sync) {
            Events.asyncEventBus().post(new SyncEvent(SyncPath.SETTINGS, null));
            this.modelIo.write();
        }
        */
    }

    private void setProperty(final Object bean, 
        final String key, final String val, final boolean logErrors,
        final HttpServletResponse resp, final boolean determineType) {
        log.info("Setting {} property on {}", key, bean);
        final Object obj;
        if (determineType) {
            obj = LanternUtils.toTyped(val);
        } else {
            obj = val;
        }
        try {
            PropertyUtils.setSimpleProperty(bean, key, obj);
            //PropertyUtils.setProperty(bean, key, obj);
        } catch (final IllegalAccessException e) {
            sendServerError(e, resp, logErrors);
        } catch (final InvocationTargetException e) {
            sendServerError(e, resp, logErrors);
        } catch (final NoSuchMethodException e) {
            sendServerError(e, resp, logErrors);
        }
    }

    /*
    private void handleContactForm(HttpServletRequest req, HttpServletResponse resp) {
        final Map<String, String> params = HttpUtils.toParamMap(req);
        String message = params.get("message");
        String email = params.get("replyto");
        try {
            new LanternFeedback().submit(message, email);
        }
        catch (final Exception e) {
            sendServerError(e, resp, true);
        }
    }
    */
}
