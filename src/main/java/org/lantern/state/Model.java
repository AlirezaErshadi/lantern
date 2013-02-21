package org.lantern.state;

import java.security.SecureRandom;
import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.annotate.JsonView;
import org.lantern.Country;
import org.lantern.LanternConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.lantern.kscope.LanternRandomRoutingTable;


/**
 * State model of the application for the UI to display.
 */
public class Model {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static class Persistent {}

    public static class Run {}

    private final SystemData system = new SystemData();

    private final Version version = new Version();

    private final Location location = new Location();

    private boolean showVis = false;

    private final boolean dev =
        LanternConstants.VERSION.equals("lantern_version_tok");

    private int ninvites = 0;

    private Modal modal = Modal.welcome;

    private Settings settings = new Settings();

    private LanternRandomRoutingTable kscopeRoutes = new LanternRandomRoutingTable();

    private Connectivity connectivity = new Connectivity();

    private Profile profile = new Profile();

    private boolean setupComplete;

    private int nproxiedSitesMax = 2000;

    private boolean launchd;

    private boolean cache;

    private String nodeId = String.valueOf(new SecureRandom().nextLong());

    private List<Country> countries = Country.allCountries();

    public SystemData getSystem() {
        return system;
    }

    public Version getVersion() {
        return version;
    }

    public Location getLocation() {
        return location;
    }

    @JsonView({Run.class, Persistent.class})
    public Modal getModal() {
        return modal;
    }

    public void setModal(final Modal modal) {
        this.modal = modal;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(final Settings settings) {
        this.settings = settings;
    }

    public LanternRandomRoutingTable getKscopeRoutes() {
        return kscopeRoutes;
    }

    public void setKscopeRoutes(final LanternRandomRoutingTable table) {
        this.kscopeRoutes = table;
    }

    @JsonView({Run.class, Persistent.class})
    public int getNinvites() {
        return ninvites;
    }

    public void setNinvites(int ninvites) {
        this.ninvites = ninvites;
    }

    @JsonView({Run.class, Persistent.class})
    public boolean isShowVis() {
        return showVis;
    }

    public void setShowVis(boolean showVis) {
        this.showVis = showVis;
    }

    public Connectivity getConnectivity() {
        return connectivity;
    }

    public void setConnectivity(Connectivity connectivity) {
        this.connectivity = connectivity;
    }

    public boolean isDev() {
        return dev;
    }

    @JsonView({Run.class, Persistent.class})
    public boolean isSetupComplete() {
        return setupComplete;
    }

    public void setSetupComplete(final boolean setupComplete) {
        this.setupComplete = setupComplete;
    }

    @JsonView({Run.class, Persistent.class})
    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    @JsonIgnore
    public boolean isLaunchd() {
        return launchd;
    }

    public void setLaunchd(boolean launchd) {
        this.launchd = launchd;
    }

    @JsonIgnore
    public boolean isCache() {
        return cache;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }

    @JsonView({Run.class, Persistent.class})
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    @JsonView({Run.class})
    public int getNproxiedSitesMax() {
        return nproxiedSitesMax;
    }

    public void setNproxiedSitesMax(int nproxiedSitesMax) {
        this.nproxiedSitesMax = nproxiedSitesMax;
    }

    public List<Country> getCountries() {
        return countries;
    }

    public void setCountries(List<Country> countries) {
        this.countries = countries;
    }
}
