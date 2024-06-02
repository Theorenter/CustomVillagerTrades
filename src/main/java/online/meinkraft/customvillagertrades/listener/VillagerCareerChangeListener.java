package online.meinkraft.customvillagertrades.listener;

import online.meinkraft.customvillagertrades.exception.VillagerNotMerchantException;
import online.meinkraft.customvillagertrades.trade.CustomTrade;
import online.meinkraft.customvillagertrades.trade.CustomTradeManager;
import online.meinkraft.customvillagertrades.villager.VillagerData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

import online.meinkraft.customvillagertrades.CustomVillagerTrades;
import online.meinkraft.customvillagertrades.villager.VillagerManager;
import org.bukkit.inventory.Merchant;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class VillagerCareerChangeListener implements Listener {

    private final CustomVillagerTrades plugin;

    public VillagerCareerChangeListener(CustomVillagerTrades plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVillagerCareerChangeEvent(VillagerCareerChangeEvent event) {

        if(event.getEntityType() != EntityType.VILLAGER) {
            return;
        }
        VillagerManager villagerManager = plugin.getVillagerManager();
        Villager villager = event.getEntity();
        villagerManager.remove(villager);

        if(villager.getProfession() != Villager.Profession.NITWIT && villager.getProfession() != Villager.Profession.NONE)
            return;

        new BukkitRunnable() {
            @Override
            public void run () {
                CustomTradeManager tradeManager = plugin.getCustomTradeManager();
                VillagerData villagerData = villagerManager.loadVillagerData(villager);

                for(int i = 0; i < villager.getRecipeCount(); i++) {

                    List<CustomTrade> trades;
                    try {
                        trades = tradeManager.getValidTrades(villager, villagerData);
                    } catch (VillagerNotMerchantException e) {
                        return;
                    }

                    if (trades.size() == 0) {
                        // don't allow villager to acquire vanilla trade if they are disabled
                        if (
                                !plugin.isVanillaTradesAllowed() ||
                                        plugin.isVanillaTradesDisabledForProfession(villager.getProfession())
                        ) {
                            event.setCancelled(true);
                        }
                    } else {
                        CustomTrade trade = tradeManager.chooseRandomTrade(trades);

                        // it can happen if all of the trades have a zero chance
                        if (trade != null) {
                            // chance of not getting the trade (if vanilla trades aren't disabled)
                            if (
                                    plugin.isVanillaTradesAllowed() &&
                                            !plugin.isVanillaTradesDisabledForProfession(villager.getProfession()) &&
                                            new Random().nextDouble() > trade.getChance()
                            ) {
                                // keep vanilla trade
                                //villager.setRecipe(event.getRecipe());
                            } else {
                                // set custom trade
                                villager.setRecipe(i, trade.getRecipe());
                                villagerData.addCustomTradeKey(i, trade.getKey());
                            }
                        }
                    }
                }
                villagerManager.saveVillagerData(villagerData);
            }
        }.runTaskLater(plugin, 1);
    }

}