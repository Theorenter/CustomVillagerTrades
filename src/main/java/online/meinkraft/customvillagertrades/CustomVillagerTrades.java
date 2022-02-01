package online.meinkraft.customvillagertrades;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import net.milkbowl.vault.economy.Economy;
import online.meinkraft.customvillagertrades.command.DisableCommand;
import online.meinkraft.customvillagertrades.command.EnableCommand;
import online.meinkraft.customvillagertrades.command.ReloadCommand;
import online.meinkraft.customvillagertrades.command.RerollCommand;
import online.meinkraft.customvillagertrades.command.RestoreCommand;
import online.meinkraft.customvillagertrades.exception.EconomyNotAvailableException;
import online.meinkraft.customvillagertrades.exception.VaultNotAvailableException;
import online.meinkraft.customvillagertrades.listener.InventoryClickListener;
import online.meinkraft.customvillagertrades.listener.InventoryCloseListener;
import online.meinkraft.customvillagertrades.listener.PlayerInteractEntityListener;
import online.meinkraft.customvillagertrades.listener.TradeSelectListener;
import online.meinkraft.customvillagertrades.listener.VillagerAcquireTradeListener;
import online.meinkraft.customvillagertrades.listener.VillagerCareerChangeListener;
import online.meinkraft.customvillagertrades.listener.VillagerDeathEventListener;
import online.meinkraft.customvillagertrades.trade.CustomTradeManager;
import online.meinkraft.customvillagertrades.trade.VanillaTrade;
import online.meinkraft.customvillagertrades.villager.VillagerData;
import online.meinkraft.customvillagertrades.villager.VillagerManager;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;

public class CustomVillagerTrades extends JavaPlugin implements PluginConfig {

    private File tradesConfigFile;
    private FileConfiguration tradesConfig;

    //private Map<UUID, VillagerData> villagers = new HashMap<>();
    
    private boolean loaded = false;

    // economy
    private Economy economy = null;

    // configuration variables
    private boolean isVanillaTradesAllowed = false;
    private boolean isDuplicateTradesAllowed = false;
    private boolean isEconomyEnabled = false;
    private Material toolMaterial;
    private Material currencyMaterial;
    private String currencyPrefix;
    private String currencySuffix;

    private VillagerManager villagerManager;
    private CustomTradeManager customTradeManager;

    private final VillagerAcquireTradeListener villagerAcquireTradeListener = new VillagerAcquireTradeListener(this);
    private final PlayerInteractEntityListener playerInteractEntityListener = new PlayerInteractEntityListener(this);
    private final VillagerDeathEventListener villagerDeathEventListener = new VillagerDeathEventListener(this);
    private final VillagerCareerChangeListener villagerCareerChangeListener = new VillagerCareerChangeListener(this);
    private final InventoryClickListener inventoryClickListener = new InventoryClickListener(this);
    private final TradeSelectListener tradeSelectListener = new TradeSelectListener(this);
    private final InventoryCloseListener inventoryCloseListener = new InventoryCloseListener(this);

    public CustomVillagerTrades() { super(); }

    protected CustomVillagerTrades(
        JavaPluginLoader loader, 
        PluginDescriptionFile description, 
        File dataFolder, 
        File file
    )
    {
        super(loader, description, dataFolder, file);
    }

    @Override
    public void onLoad() {

        // register ConfigurationSerializable classes
        ConfigurationSerialization.registerClass(VillagerData.class);
        ConfigurationSerialization.registerClass(VanillaTrade.class);

    }

    @Override
    public void onEnable() {
        
        // ensure plugin doesn't get enabled more than once
        if(loaded) {
            getLogger().warning("Plugin already enabled");
            return;
        }

        // create config file if it doesn't exist
        createConfig();

        // set config values
        isDuplicateTradesAllowed = getConfig().getBoolean("allowDuplicateTrades");
        isVanillaTradesAllowed = !getConfig().getBoolean("disableVanillaTrades");
        isEconomyEnabled = getConfig().getBoolean("enableEconomy");
        toolMaterial = Material.getMaterial(getConfig().getString("tool"));
        currencyMaterial = Material.getMaterial(getConfig().getString("currency"));
        currencyPrefix = getConfig().getString("currencyPrefix");
        currencySuffix = getConfig().getString("currencySuffix");

        // setup economy
        if(isEconomyEnabled()) {
            try {
                isEconomyEnabled = false;
                if (setupEconomy()) {
                    isEconomyEnabled = true;
                }
                else {
                    getLogger().warning(
                        "No economy provider (money items will not work as expect)!\n" +
                        "If you do not have Vault and a compatible economy plugin, ensure enableEconomy is set to false in the config.yml"
                    );
                }
            } catch (VaultNotAvailableException | EconomyNotAvailableException exception) {
                getLogger().warning(exception.getMessage());   
            }
        }

        // create the custom trade
        createTradesConfig();

        // load villager manager
        villagerManager = new VillagerManager(this);
        villagerManager.load("villagers.data");

        // load custom trades
        customTradeManager = new CustomTradeManager(this);
        customTradeManager.load();
        

        // register listeners
        getServer().getPluginManager().registerEvents(
            villagerAcquireTradeListener, 
            this
        );
        getServer().getPluginManager().registerEvents(
            playerInteractEntityListener,
            this
        );
        getServer().getPluginManager().registerEvents(
            villagerDeathEventListener,
            this
        );
        getServer().getPluginManager().registerEvents(
            villagerCareerChangeListener,
            this
        );
        getServer().getPluginManager().registerEvents(
            inventoryClickListener,
            this
        );
        getServer().getPluginManager().registerEvents(
            tradeSelectListener,
            this
        );
        getServer().getPluginManager().registerEvents(
            inventoryCloseListener,
            this
        );

        // register commands
        this.getCommand("reroll").setExecutor(new RerollCommand(this));
        this.getCommand("restore").setExecutor(new RestoreCommand(this));
        this.getCommand("enable").setExecutor(new EnableCommand(this));
        this.getCommand("disable").setExecutor(new DisableCommand(this));
        this.getCommand("reload").setExecutor(new ReloadCommand(this));

        // ensure plugin doesn't get enabled more than once
        this.loaded = true;

    }
    
    @Override
    public void onDisable() {

        // ensure plugin doesn't get disabled more than once
        if(!loaded) {
            getLogger().warning("Plugin already disabled");
            return;
        }

        // unregister listeners
        HandlerList.unregisterAll(villagerAcquireTradeListener);
        HandlerList.unregisterAll(playerInteractEntityListener);
        HandlerList.unregisterAll(villagerDeathEventListener);
        HandlerList.unregisterAll(villagerCareerChangeListener);
        HandlerList.unregisterAll(inventoryClickListener);
        HandlerList.unregisterAll(tradeSelectListener);
        HandlerList.unregisterAll(inventoryCloseListener);
        
        // save data files
        villagerManager.save("villagers.data");

        // allow plugin to be enabled again
        this.loaded = false;

    }

    @Override
    public FileConfiguration getTradesConfig() {
        return tradesConfig;
    }

    @Override
    public boolean isDuplicateTradesAllowed() {
        return isDuplicateTradesAllowed;
    }

    @Override
    public boolean isVanillaTradesAllowed() {
        return isVanillaTradesAllowed;
    }

    @Override
    public Material getToolMaterial() {
        return toolMaterial;
    }

    @Override
    public boolean isEconomyEnabled() {
        return isEconomyEnabled;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public CustomTradeManager getCustomTradeManager() {
        return this.customTradeManager;
    }
    
    public VillagerManager getVillagerManager() {
        return villagerManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    private void createConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void createTradesConfig() {

        tradesConfigFile = new File(getDataFolder(), "trades.yml");
        if (!tradesConfigFile.exists()) {
            tradesConfigFile.getParentFile().mkdirs();
            saveResource("trades.yml", false);
         }

        tradesConfig = new YamlConfiguration();
        try {
            tradesConfig.load(tradesConfigFile);
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().warning(
                "Failed to read trades.yml: " +
                exception.getMessage()
            );
        }
        
    }

    private boolean setupEconomy() throws VaultNotAvailableException, EconomyNotAvailableException {

        // get vault plugin
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            throw new VaultNotAvailableException();
        }
        
        // get economy plugin
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) throw new EconomyNotAvailableException();

        economy = rsp.getProvider();
        return economy != null;

    }

    @Override
    public Material getCurrencyMaterial() {
        return this.currencyMaterial;
    }

    @Override
    public String getCurrencyPrefix() {
        return this.currencyPrefix;
    }


    @Override
    public String getCurrencySuffix() {
        return this.currencySuffix;
    }


    
    
}

