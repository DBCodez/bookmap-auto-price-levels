package com.bookmap.addons.autopricelevels;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.Layer1ApiFinishable;
import velox.api.layer1.Layer1ApiInstrumentListener;
import velox.api.layer1.Layer1ApiInstrumentSpecificEnabledStateProvider;
import velox.api.layer1.Layer1ApiProvider;
import velox.api.layer1.Layer1CustomPanelsGetter;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1Attachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.annotations.UnrestrictedData;
import velox.api.layer1.common.ListenableHelper;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.MarketMode;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.messages.Layer1ApiUserMessageReloadStrategyGui;
import velox.api.layer1.messages.indicators.SettingsAccess;
import velox.api.layer1.settings.Layer1ConfigSettingsInterface;
import velox.config.beans.BookMapSettings;
import velox.config.beans.ChartSettings;
import velox.config.beans.StrategySettingsEntry;
import velox.gui.StrategyPanel;

/**
 * Auto Price Levels Add-On for Bookmap L1 API (v3 - Bookmap 8.0+ Obfuscation-Proof).
 *
 * Dynamically discovers Bookmap's settings holder and strategy manager via public
 * non-obfuscated L1 API types from SettingsAccess, with zero hardcoded obfuscated
 * class, method, or field names. Works across Bookmap 7.x, 8.0, and future 8.x/9.x builds.
 *
 * Automatically enables Bookmap's built-in "Price Levels" (v1.07, settings version 5)
 * indicator on every open and newly subscribed ticker with:
 * - Highest Price Level (100%): Red (-65536)
 * - Middle Price Level (50%): Yellow (-256)
 * - Lowest Price Level (0%): Green (-16711936)
 * - Scheduled resets: AT 00:00:00 (America/New_York)
 */
@Layer1Attachable
@Layer1StrategyName("Auto Price Levels")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
@UnrestrictedData
public class AutoPriceLevels implements
        Layer1ApiFinishable,
        Layer1ApiInstrumentListener,
        Layer1ApiDataListener,
        Layer1ConfigSettingsInterface,
        Layer1CustomPanelsGetter {

    private static final String BUILTIN_STRATEGY_NAME = "Price Levels";
    private static final String BUILTIN_INDICATOR_CLASS = "com.bookmap.addons.pricelevelsindicator.PriceLevelsIndicator";
    private static final int BUILTIN_SETTINGS_VERSION = 5;

    private final Layer1ApiProvider provider;
    private final Set<String> initializedCharts = Collections.synchronizedSet(new HashSet<String>());

    public volatile boolean enableHigh = true;
    public volatile boolean enableMid = true;
    public volatile boolean enableLow = true;
    public volatile boolean enableScheduledReset = true;
    public volatile boolean forceAllChartsOnNextTick = true;

    private volatile Object settingsHolder;
    private volatile Method[] settingsGetterMethods;
    private volatile Object strategyManager;
    private volatile Method listStrategiesMethod;
    private volatile int inactiveTicks = 0;

    private ScheduledExecutorService scheduler;

    public AutoPriceLevels(Layer1ApiProvider provider) {
        this.provider = provider;
        if (provider != null) {
            ListenableHelper.addListeners(provider, this);
        }
        Log.info("AutoPriceLevels: Initializing v3.1 (Bookmap 8.0+ dynamic binding, schema v5)");
        startAutoEnableSynchronizer();
    }

    private void startAutoEnableSynchronizer() {
        scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AutoPriceLevels-Sync");
                t.setDaemon(true);
                return t;
            }
        });
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    syncBookmapSettings();
                } catch (Throwable t) {
                    Log.error("AutoPriceLevels: Error in syncBookmapSettings", t);
                }
            }
        }, 50L, 50L, TimeUnit.MILLISECONDS);
    }

    private boolean tryBindHolder(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Method[] methods = candidate.getClass().getMethods();
        List<Method> getters = new ArrayList<Method>();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (m.getParameterCount() == 0 && m.getReturnType() == BookMapSettings.class) {
                m.setAccessible(true);
                getters.add(m);
            }
        }
        if (getters.isEmpty()) {
            return false;
        }
        this.settingsHolder = candidate;
        this.settingsGetterMethods = getters.toArray(new Method[0]);
        return true;
    }

    @Override
    public void acceptSettingsInterface(SettingsAccess settingsAccess) {
        try {
            if (settingsAccess == null) {
                return;
            }
            Field[] fields = settingsAccess.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field f1 = fields[i];
                f1.setAccessible(true);
                Object v1 = f1.get(settingsAccess);
                if (v1 == null) {
                    continue;
                }
                if (this.strategyManager == null) {
                    Method[] dm = v1.getClass().getDeclaredMethods();
                    for (int j = 0; j < dm.length; j++) {
                        Method m = dm[j];
                        if (m.getParameterCount() == 0
                                && m.getReturnType() == List.class
                                && Modifier.isPublic(m.getModifiers())) {
                            m.setAccessible(true);
                            this.strategyManager = v1;
                            this.listStrategiesMethod = m;
                            break;
                        }
                    }
                }
                if (this.settingsHolder == null) {
                    if (!tryBindHolder(v1)) {
                        Field[] innerFields = v1.getClass().getDeclaredFields();
                        for (int k = 0; k < innerFields.length; k++) {
                            Field f2 = innerFields[k];
                            f2.setAccessible(true);
                            Object v2 = f2.get(v1);
                            if (tryBindHolder(v2)) {
                                break;
                            }
                        }
                    }
                }
            }
            Log.info("AutoPriceLevels v3.1: Bound settingsHolder=" + (settingsHolder != null)
                    + ", strategyManager=" + (strategyManager != null));
        } catch (Throwable t) {
            Log.error("AutoPriceLevels: Failed in acceptSettingsInterface", t);
        }
    }

    private int checkSelfInStrategyManager() {
        Object mgr = this.strategyManager;
        Method listMethod = this.listStrategiesMethod;
        if (mgr == null || listMethod == null) {
            return -1;
        }
        try {
            List<?> list = (List<?>) listMethod.invoke(mgr, new Object[0]);
            if (list == null) {
                return 0;
            }
            Object[] arr = list.toArray();
            for (int i = 0; i < arr.length; i++) {
                Object h = arr[i];
                if (h == null) {
                    continue;
                }
                Field[] hFields = h.getClass().getDeclaredFields();
                for (int j = 0; j < hFields.length; j++) {
                    Field f = hFields[j];
                    if (f.getType() == Object.class) {
                        f.setAccessible(true);
                        if (f.get(h) == this) {
                            return 1;
                        }
                    }
                }
            }
            return 0;
        } catch (Throwable t) {
            return -1;
        }
    }

    public String buildPriceLevelsJson() {
        return "{\n"
                + "  \"isIndicatorEnabled\": true,\n"
                + "  \"isResetEnabled\": " + enableScheduledReset + ",\n"
                + "  \"resetAtTimeNs\": 0,\n"
                + "  \"resetEveryTimeNs\": 0,\n"
                + "  \"resetType\": \"AT\",\n"
                + "  \"settings\": {\n"
                + "    \"Highest Price Level\": {\n"
                + "      \"color\": {\n"
                + "        \"value\": -65536,\n"
                + "        \"frgbvalue\": null,\n"
                + "        \"fvalue\": null,\n"
                + "        \"falpha\": 0.0,\n"
                + "        \"cs\": null\n"
                + "      },\n"
                + "      \"percent\": 1.0,\n"
                + "      \"isEnabled\": " + enableHigh + "\n"
                + "    },\n"
                + "    \"Middle Price Level\": {\n"
                + "      \"color\": {\n"
                + "        \"value\": -256,\n"
                + "        \"frgbvalue\": null,\n"
                + "        \"fvalue\": null,\n"
                + "        \"falpha\": 0.0,\n"
                + "        \"cs\": null\n"
                + "      },\n"
                + "      \"percent\": 0.5,\n"
                + "      \"isEnabled\": " + enableMid + "\n"
                + "    },\n"
                + "    \"Lowest Price Level\": {\n"
                + "      \"color\": {\n"
                + "        \"value\": -16711936,\n"
                + "        \"frgbvalue\": null,\n"
                + "        \"fvalue\": null,\n"
                + "        \"falpha\": 0.0,\n"
                + "        \"cs\": null\n"
                + "      },\n"
                + "      \"percent\": 0.0,\n"
                + "      \"isEnabled\": " + enableLow + "\n"
                + "    }\n"
                + "  }\n"
                + "}";
    }

    public void syncBookmapSettings() {
        Object holder = this.settingsHolder;
        Method[] getters = this.settingsGetterMethods;
        if (holder == null || getters == null) {
            return;
        }
        int active = checkSelfInStrategyManager();
        if (active == 0) {
            int ticks = inactiveTicks + 1;
            inactiveTicks = ticks;
            if (ticks > 60) {
                finish();
            }
            return;
        }
        if (active == 1) {
            inactiveTicks = 0;
        }

        boolean forceAll = forceAllChartsOnNextTick;
        if (forceAll) {
            forceAllChartsOnNextTick = false;
            initializedCharts.clear();
        }
        String json = buildPriceLevelsJson();
        StrategySettingsEntry entry = new StrategySettingsEntry(BUILTIN_SETTINGS_VERSION, json);

        for (int i = 0; i < getters.length; i++) {
            try {
                BookMapSettings bmSettings = (BookMapSettings) getters[i].invoke(holder, new Object[0]);
                if (bmSettings != null) {
                    ChartSettings lastUsed = bmSettings.getLastUsedChartSettings();
                    if (lastUsed != null && lastUsed.getStrategiesSettings() != null) {
                        lastUsed.getStrategiesSettings().put(BUILTIN_STRATEGY_NAME, new StrategySettingsEntry(entry));
                    }
                    applyToChartsList(bmSettings.getChartsSettings(), entry, forceAll);
                }
            } catch (Throwable ignored) {
            }
        }

        enableLivePriceLevels(forceAll);
    }

    private void applyToChartsList(List<ChartSettings> charts, StrategySettingsEntry entry, boolean forceAll) {
        if (charts == null) {
            return;
        }
        Object[] snapshot = charts.toArray();
        for (int i = 0; i < snapshot.length; i++) {
            ChartSettings cs = (ChartSettings) snapshot[i];
            if (cs == null) {
                continue;
            }
            Map<String, StrategySettingsEntry> map = cs.getStrategiesSettings();
            if (map == null) {
                continue;
            }
            String id = cs.chartId();
            StrategySettingsEntry existing = map.get(BUILTIN_STRATEGY_NAME);
            boolean needsUpdate = forceAll
                    || existing == null
                    || existing.version != BUILTIN_SETTINGS_VERSION
                    || existing.text == null
                    || existing.text.contains("\"isIndicatorEnabled\": false")
                    || existing.text.contains("\"isIndicatorEnabled\":false")
                    || (id != null && !initializedCharts.contains(id));
            if (needsUpdate) {
                map.put(BUILTIN_STRATEGY_NAME, new StrategySettingsEntry(entry));
                if (id != null) {
                    initializedCharts.add(id);
                }
            }
        }
    }

    private void enableLivePriceLevels(boolean forceAll) {
        Object mgr = this.strategyManager;
        Method listMethod = this.listStrategiesMethod;
        if (mgr == null || listMethod == null) {
            return;
        }
        try {
            List<?> list = (List<?>) listMethod.invoke(mgr, new Object[0]);
            if (list == null) {
                return;
            }
            Object indicator = null;
            Layer1ApiInstrumentSpecificEnabledStateProvider stateProvider = null;
            Object[] arr = list.toArray();
            for (int i = 0; i < arr.length; i++) {
                Object h = arr[i];
                if (h == null) {
                    continue;
                }
                Object candInd = null;
                Layer1ApiInstrumentSpecificEnabledStateProvider candProv = null;
                Field[] hFields = h.getClass().getDeclaredFields();
                for (int j = 0; j < hFields.length; j++) {
                    Field f = hFields[j];
                    if (f.getType() == Object.class) {
                        f.setAccessible(true);
                        Object v = f.get(h);
                        if (v != null && BUILTIN_INDICATOR_CLASS.equals(v.getClass().getName())) {
                            candInd = v;
                        }
                    } else if (f.getType() == Layer1ApiInstrumentSpecificEnabledStateProvider.class) {
                        f.setAccessible(true);
                        candProv = (Layer1ApiInstrumentSpecificEnabledStateProvider) f.get(h);
                    }
                }
                if (candInd != null && candProv != null) {
                    indicator = candInd;
                    stateProvider = candProv;
                    break;
                }
            }
            if (indicator == null || stateProvider == null) {
                return;
            }

            Map<?, ?> mapH = null;
            Object lockL = null;
            Layer1ApiProvider indProvider = null;
            Field[] indFields = indicator.getClass().getDeclaredFields();
            for (int i = 0; i < indFields.length; i++) {
                Field f = indFields[i];
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                Class<?> ft = f.getType();
                if (ft == Map.class) {
                    String sig = f.getGenericType().getTypeName();
                    if ((sig != null && sig.contains("PriceLevelsIndicatorSettings"))
                            || (mapH == null && "h".equals(f.getName()))) {
                        mapH = (Map<?, ?>) f.get(indicator);
                    }
                } else if (ft == Object.class) {
                    lockL = f.get(indicator);
                } else if (ft == Layer1ApiProvider.class) {
                    indProvider = (Layer1ApiProvider) f.get(indicator);
                }
            }

            if (mapH == null || lockL == null) {
                return;
            }

            Object[] keys;
            synchronized (lockL) {
                keys = mapH.keySet().toArray();
            }
            for (int i = 0; i < keys.length; i++) {
                String alias = (String) keys[i];
                if (alias == null) {
                    continue;
                }
                if (forceAll || !stateProvider.isStrategyEnabled(alias)) {
                    synchronized (lockL) {
                        mapH.remove(alias);
                    }
                    stateProvider.onStrategyCheckboxEnabled(alias, true);
                    if (indProvider != null) {
                        indProvider.sendUserMessage(new Layer1ApiUserMessageReloadStrategyGui());
                    }
                    Log.info("AutoPriceLevels v3: Enabled Price Levels (v5) for " + alias);
                }
            }
        } catch (Throwable t) {
            Log.error("AutoPriceLevels: Error in enableLivePriceLevels", t);
        }
    }

    @Override
    public void onInstrumentAdded(String alias, InstrumentInfo instrumentInfo) {
    }

    @Override
    public void onInstrumentRemoved(String alias) {
    }

    @Override
    public void onInstrumentAlreadySubscribed(String symbol, String exchange, String type) {
    }

    @Override
    public void onInstrumentNotFound(String symbol, String exchange, String type) {
    }

    @Override
    public void onTrade(String alias, double price, int size, TradeInfo tradeInfo) {
    }

    @Override
    public void onDepth(String alias, boolean isBid, int price, int size) {
    }

    @Override
    public void onMarketMode(String alias, MarketMode marketMode) {
    }

    @Override
    public StrategyPanel[] getCustomGuiFor(String alias, String indicatorName) {
        StrategyPanel panel = new StrategyPanel("Auto Price Levels (Global Template)", new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 6, 4, 6);

        JLabel statusLabel = new JLabel("Status: Auto-enabling Price Levels (Bookmap 8.0+ v3.1)");
        panel.add(statusLabel, gbc);

        gbc.gridy++;
        final JCheckBox cbHigh = new JCheckBox("Highest Price Level (100% - Red)", enableHigh);
        panel.add(cbHigh, gbc);

        gbc.gridy++;
        final JCheckBox cbMid = new JCheckBox("Middle Price Level (50% - Yellow)", enableMid);
        panel.add(cbMid, gbc);

        gbc.gridy++;
        final JCheckBox cbLow = new JCheckBox("Lowest Price Level (0% - Green)", enableLow);
        panel.add(cbLow, gbc);

        gbc.gridy++;
        final JCheckBox cbReset = new JCheckBox("Scheduled Reset (AT 00:00:00 America/New_York)", enableScheduledReset);
        panel.add(cbReset, gbc);

        ActionListener toggleListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    enableHigh = cbHigh.isSelected();
                    enableMid = cbMid.isSelected();
                    enableLow = cbLow.isSelected();
                    enableScheduledReset = cbReset.isSelected();
                    forceAllChartsOnNextTick = true;
                } catch (Throwable ignored) {
                }
            }
        };
        cbHigh.addActionListener(toggleListener);
        cbMid.addActionListener(toggleListener);
        cbLow.addActionListener(toggleListener);
        cbReset.addActionListener(toggleListener);

        gbc.gridy++;
        JButton applyAllBtn = new JButton("Force Enable on All Saved Tickers Now");
        applyAllBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    forceAllChartsOnNextTick = true;
                } catch (Throwable ignored) {
                }
            }
        });
        panel.add(applyAllBtn, gbc);

        return new StrategyPanel[]{panel};
    }

    @Override
    public void finish() {
        ScheduledExecutorService s = scheduler;
        if (s != null) {
            scheduler = null;
            s.shutdownNow();
        }
    }
}
