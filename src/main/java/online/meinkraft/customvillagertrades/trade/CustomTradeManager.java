package online.meinkraft.customvillagertrades.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import net.md_5.bungee.api.ChatColor;
import online.meinkraft.customvillagertrades.CustomVillagerTrades;
import online.meinkraft.customvillagertrades.exception.VillagerNotMerchantException;
import online.meinkraft.customvillagertrades.util.WeightedCollection;
import online.meinkraft.customvillagertrades.villager.VillagerData;
import online.meinkraft.customvillagertrades.villager.VillagerManager;

public class CustomTradeManager {

    private Map<String, CustomTrade> customTrades = new HashMap<>();
    private final CustomVillagerTrades plugin;
    private final VillagerManager villagerManager;

    public CustomTradeManager(
        CustomVillagerTrades plugin
    ) {
        this.plugin = plugin;
        this.villagerManager = plugin.getVillagerManager();
    }

    public void load() {
        customTrades = CustomTradeLoader.loadTrades(plugin);
    }

    public List<CustomTrade> getCustomTrades() {
        return customTrades.values().stream().toList();
    }

    public CustomTrade getCustomTrade(String key) {
        return customTrades.get(key);
    }

    public void forceCustomTrade(Villager villager, CustomTrade customTrade) {

        VillagerData data = villagerManager.getData(villager);
        if(data == null) data = villagerManager.addVillager(villager);

        data.addVanillaTrade(
            villager.getVillagerLevel(), 
            customTrade.getRecipe()
        );

        data.addCustomTradeKey(
            data.getVanillaTrades().size() - 1, 
            customTrade.getKey()
        );

        Merchant merchant = (Merchant) villager;
        List<MerchantRecipe> recipes = new ArrayList<>();
        recipes.addAll(merchant.getRecipes());
        recipes.add(customTrade.getRecipe());
        merchant.setRecipes(recipes);

    }

    public void removeCustomTrade(Villager villager, CustomTrade trade) {

        VillagerData data = villagerManager.getData(villager);
        if(data == null) return;

        data.removeCustomTrade(trade);

        Merchant merchant = (Merchant) villager;
        List<MerchantRecipe> recipes = new ArrayList<>();

        for(int index = 0; index < data.getVanillaTrades().size(); index ++) {
            VanillaTrade vanillaTrade = data.getVanillaTrade(index);
            boolean isCustomTrade = data.isCustomTrade(index);
            String customTradeKey = data.getCustomTradeKey(index);
            CustomTrade customTrade = getCustomTrade(customTradeKey);
            if(isCustomTrade) recipes.add(customTrade.getRecipe());
            else recipes.add(vanillaTrade.getRecipe());
        }

        merchant.setRecipes(recipes);

    }

    public void refreshTrades(Villager villager, Player player) {

        Merchant merchant = (Merchant) villager;

        VillagerManager villagerManager = plugin.getVillagerManager();
        VillagerData data = villagerManager.getData((Villager) merchant);

        List<MerchantRecipe> oldRecipes = merchant.getRecipes();
        List<MerchantRecipe> newRecipes = new ArrayList<>();
        
        int customTradeIndex = 0;
        for(int index = 0; index < oldRecipes.size(); index++) {
            if(data.isCustomTrade(index)) {

                // get the corresponding customTradeKey for this index
                String customTradeKey = data.getCustomTradeKey(customTradeIndex);
                customTradeIndex++;

                CustomTrade customTrade = customTrades.get(customTradeKey);

                // replace old trade with new trade if it is a custom trade
                if(customTrade != null) {

                    // the villager might forget the trade if it is not valid
                    // anymore
                    if(plugin.forgetInvalidCustomTrades()) {

                        List<CustomTrade> validCustomTrades;
                        try {
                            validCustomTrades = getValidTrades(villager, true);
                        } catch (VillagerNotMerchantException e) {
                            validCustomTrades = new ArrayList<>();
                        }

                        if(!validCustomTrades.contains(customTrade)) {

                            if(player != null) player.sendMessage(
                                ChatColor.YELLOW + 
                                "The villager no longer trades " + 
                                ChatColor.AQUA + customTrade.getKey()
                            );

                            newRecipes.add(data.getVanillaTrade(index).getRecipe());
                            data.removeCustomTrade(customTrade);
                            continue;
                                
                        }

                    }

                    MerchantRecipe oldRecipe = oldRecipes.get(index);
                    MerchantRecipe newRecipe = customTrade.getRecipe();

                    // set the uses and special price of the previous recipe so 
                    // that players cant continually refresh uses and price by 
                    // closing and opening the trade window
                    newRecipe.setUses(oldRecipe.getUses());
                    newRecipe.setSpecialPrice(oldRecipe.getSpecialPrice());

                    // add updated recipe
                    newRecipes.add(newRecipe);
                    
                }
                else  newRecipes.add(oldRecipes.get(index));

            }
            else {
                newRecipes.add(oldRecipes.get(index));
            }
        }

        merchant.setRecipes(newRecipes);

    }

    public void refreshTrades(Villager villager) {
        refreshTrades(villager, null);
    }

    public List<CustomTrade> getValidTrades(Villager villager, boolean ingoreDuplicates) throws VillagerNotMerchantException {

        List<CustomTrade> validTrades = new ArrayList<>();
        
        if(!(villager instanceof Merchant)) {
            throw new VillagerNotMerchantException();
        }

        List<CustomTrade> allTrades = customTrades.values().stream().toList();

        for(CustomTrade trade : allTrades) {

            List<Villager.Profession> professions = trade.getProfessions();
            List<Integer> levels = trade.getLevels();
            List<Villager.Type> villagerTypes = trade.getVillagerTypes();
            List<Biome> biomes = trade.getBiomes();

            // trader must have the right profession(s)
            if(
                professions.size() > 0 && 
                !professions.contains(villager.getProfession())
            ) {
                continue;
            }

            // trader must have the right level(s)
            if(
                levels.size() > 0 && 
                !levels.contains(villager.getVillagerLevel())
            ) {
                continue;
            }

            // trader must be of the right type(s)
            if(
                villagerTypes.size() > 0 && 
                !villagerTypes.contains(villager.getVillagerType())
            ) {
                continue;
            }

            // trader must be in the right biome(s)
            if(biomes.size() > 0) {
                Location location = villager.getLocation();
                Biome biome = location.getWorld().getBiome(location);
                if(!biomes.contains(biome)) continue;
            }
            
            // tader can't sell the same type of thing more than once
            if(
                !ingoreDuplicates &&
                !plugin.isDuplicateTradesAllowed() &&
                villagerHasCustomTrade(villager, trade)
            ) {
                continue;
            }
            
            // add to list of potential trades to return
            validTrades.add(trade);

        }

        return validTrades;

    }

    public List<CustomTrade> getValidTrades(Villager villager) throws VillagerNotMerchantException {
        return getValidTrades(villager, false);
    }

    public boolean villagerHasCustomTrade(Villager villager, CustomTrade trade) {

        VillagerData data = villagerManager.getData(villager);
        return data.getCustomTradeKeys().contains(trade.getKey());

    }

    public CustomTrade chooseRandomTrade(List<CustomTrade> trades) {

        if(trades.size() == 0) return null;
        WeightedCollection<CustomTrade> weightedTrades = new WeightedCollection<>();
        trades.forEach(trade -> {
            if(trade.getChance() == 0) return;
            weightedTrades.add(100 * trade.getChance(), trade);
        });
        if(weightedTrades.size() == 0) return null;
        return weightedTrades.next();

    }

    public boolean rerollCustomTrades(Villager villager) throws VillagerNotMerchantException {

        if(!(villager instanceof Merchant)) {
            throw new VillagerNotMerchantException();
        }

        Random rand = new Random();
        
        VillagerData data = villagerManager.getData(villager);
        data.clearCustomTradeKeys();
        List<VanillaTrade> vanillaTrades = data.getVanillaTrades();

        int villagerLevel = villager.getVillagerLevel();
        List<MerchantRecipe> newRecipes = new ArrayList<>();

        VanillaTrade vanillaTrade;
        for(int index = 0; index < vanillaTrades.size(); index++) {
            vanillaTrade = vanillaTrades.get(index);

            villager.setVillagerLevel(vanillaTrade.getVillagerLevel());
            List<CustomTrade> validCustomTrades = getValidTrades(villager);

            // no trades available - continue
            if(validCustomTrades.size() == 0) {
                // keep vanilla trade
                newRecipes.add(vanillaTrade.getRecipe());
            }
            else {
                CustomTrade customTrade = chooseRandomTrade(validCustomTrades);

                // chance of not getting the trade (if vanilla trades aren't disabled)
                if(
                    plugin.isVanillaTradesAllowed() && 
                    rand.nextDouble() > customTrade.getChance()
                ) {
                    // keep vanilla trade
                    newRecipes.add(vanillaTrade.getRecipe());
                }
                else {
                    // set custom trade
                    newRecipes.add(customTrade.getRecipe());
                    data.addCustomTradeKey(index, customTrade.getKey());
                }
            }

            villager.setRecipes(newRecipes);

        }
        
        villager.setVillagerLevel(villagerLevel);

        return true;
    
    }
 
    public void restoreVanillaTrades(Villager villager) throws VillagerNotMerchantException {

        if(!(villager instanceof Merchant)) {
            throw new VillagerNotMerchantException();
        }
        
        VillagerData data = villagerManager.getData(villager);
        
        List<VanillaTrade> vanillaTrades = data.getVanillaTrades();
        List<MerchantRecipe> recipes = new ArrayList<>();
        for(VanillaTrade vanillaTrade : vanillaTrades) {
            recipes.add(vanillaTrade.getRecipe());
        }

        villager.setRecipes(recipes);
        data.clearCustomTradeKeys();

    }

}
