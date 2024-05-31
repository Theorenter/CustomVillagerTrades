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
import org.bukkit.inventory.MerchantRecipe;
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


        // Code that performs after villager change their profession.
        //
        // VillagerAcquireTradeListener works incorrectly when new villager gets custom trades, IDK why.
        // I wrote this to fix the problem.
        new BukkitRunnable() {
            @Override
            public void run() {
                // Don't need to set custom trades if villager profession is none or nitwit
                if(villager.getProfession().equals(Villager.Profession.NONE) ||
                        villager.getProfession().equals(Villager.Profession.NITWIT)
                ) {
                    return;
                }

                // After profession changed
                CustomTradeManager tradeManager = plugin.getCustomTradeManager();

                VillagerData villagerData = villagerManager.loadVillagerData(villager);

                List<CustomTrade> trades;
                try {
                    trades = tradeManager.getValidTrades(villager, villagerData);
                } catch (VillagerNotMerchantException e) {
                    return;
                }
                for (int i = 0; i <= villager.getRecipeCount(); i++) {
                    villagerData.addVanillaTrade(villager.getVillagerLevel(), villager.getRecipe(i));
                    int index = villagerData.getVanillaTrades().size() - 1;

                    if(trades.size() == 0) {
                        // don't allow villager to acquire vanilla trade if they are disabled
                        if(
                                !plugin.isVanillaTradesAllowed() ||
                                        plugin.isVanillaTradesDisabledForProfession(villager.getProfession())
                        ) {
                            event.setCancelled(true);
                        }
                    }
                    else {
                        CustomTrade trade = tradeManager.chooseRandomTrade(trades);

                        // it can happen if all of the trades have a zero chance
                        if(trade != null) {
                            // chance of not getting the trade (if vanilla trades aren't disabled)
                            if(
                                    plugin.isVanillaTradesAllowed() &&
                                            !plugin.isVanillaTradesDisabledForProfession(villager.getProfession()) &&
                                            new Random().nextDouble() > trade.getChance()
                            ) {
                                // keep vanilla trade
                                villager.setRecipe(i, villager.getRecipe(i));
                            }
                            else {
                                // set custom trade
                                villager.setRecipe(i, trade.getRecipe());
                                villagerData.addCustomTradeKey(
                                        index,
                                        trade.getKey()
                                );
                            }
                        }
                    }
                }
                villagerManager.saveVillagerData(villagerData);
            }
        }.runTaskLater(plugin, 1);
    }

}